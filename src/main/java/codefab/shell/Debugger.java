package codefab.shell;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.CheckResult;
import codefab.checker.Checker;
import codefab.core.Diagnostic;
import codefab.core.InterpreterRuntimeError;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.executor.CodeFabArray;
import codefab.executor.CodeFabCallable;
import codefab.executor.Environment;
import codefab.executor.ExecutionObserver;
import codefab.executor.Executor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Interactive, statement-granularity debugger (contract §10). It plugs into the
 * Executor as an {@link ExecutionObserver}: the Executor calls
 * {@link #beforeStmt} synchronously, on the same thread, immediately before each
 * statement, and the debugger decides whether to pause. When it pauses it prints
 * the stop banner and any watched values, then runs a {@code >} command loop
 * (GoF Command pattern, see {@link DebugCommand}) until a resume command
 * (step/next/continue) is entered.
 *
 * <p>Both the command input ({@link BufferedReader}) and the output
 * ({@link PrintStream}) are injected, so a test can drive a whole session with a
 * scripted command string (e.g. {@code "step\nbreak 7\ncontinue\n"}).
 *
 * <p>Construct with the source path and the source text, then call {@link #run()}.
 */
public final class Debugger implements ExecutionObserver {

    /** How the debugger decides where to pause next. */
    enum StepMode {
        /** Pause at every statement. */
        STEP,
        /** Pause at the next statement whose depth is &lt;= the remembered baseline
         *  (i.e. do not descend into blocks/functions). */
        NEXT,
        /** Run until a breakpoint line is reached. */
        CONTINUE
    }

    private final String path;
    private final List<String> sourceLines;
    private final BufferedReader in;
    private final PrintStream out;

    // --- debug state -------------------------------------------------------
    private StepMode mode = StepMode.STEP;            // pause on the very first statement
    private final Set<Integer> breakpoints = new TreeSet<>();
    private final Set<String> watches = new LinkedHashSet<>();
    private int nextBaselineDepth;                    // baseline for NEXT (step over)
    private int lastDepth;                            // depth of the currently-paused stmt

    // Set on each beforeStmt so command handlers can read the live scope.
    private Environment currentEnv;

    private final Map<String, DebugCommand> commands = new HashMap<>();

    public Debugger(String path, String source, BufferedReader in, PrintStream out) {
        this.path = path;
        this.sourceLines = splitLines(source);
        this.in = in;
        this.out = out;
        registerCommands();
    }

    private static List<String> splitLines(String source) {
        // Keep an empty leading slot so sourceLines.get(line-1) maps 1-based lines.
        List<String> lines = new ArrayList<>();
        for (String line : source.split("\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    /**
     * Load, check and run the script under debugger control. Assembles the
     * pipeline; if scanning/parsing/checking produces diagnostics they are
     * printed and the session ends without executing. Otherwise the debugger is
     * attached as the Executor's observer and the folded program runs, pausing
     * per the stepping rules.
     *
     * @return a process exit code (0 = ran to completion, 65 = diagnostics).
     */
    public int run() {
        out.println("[DEBUG] 소스코드 로딩: " + path);

        List<Diagnostic> diagnostics = new ArrayList<>();
        Environment globals = new Environment();
        String joined = String.join("\n", sourceLines);

        List<Token> tokens = new Scanner(joined, diagnostics).scanTokens();
        List<Stmt> statements = new Parser(tokens, diagnostics).parse();
        if (!diagnostics.isEmpty()) {
            return reportAndStop(diagnostics);
        }

        CheckResult checked = new Checker(diagnostics).check(statements);
        if (!diagnostics.isEmpty()) {
            return reportAndStop(diagnostics);
        }

        Executor executor = new Executor(out::println, globals, checked.locals());
        executor.setObserver(this);
        try {
            executor.execute(checked.program());
        } catch (InterpreterRuntimeError error) {
            out.println(new Diagnostic(Diagnostic.Stage.RUNTIME, error.line(),
                    error.getMessage()).render());
            return 65;
        }
        return 0;
    }

    private int reportAndStop(List<Diagnostic> diagnostics) {
        for (Diagnostic d : diagnostics) {
            out.println(d.render());
        }
        return 65;
    }

    // --- observer hook -----------------------------------------------------

    @Override
    public void beforeStmt(Stmt stmt, int line, Environment env, int depth) {
        this.currentEnv = env;
        this.lastDepth = depth;
        if (!shouldStop(line, depth)) {
            return;
        }
        printStop(line, breakpoints.contains(line));
        printWatchValues();
        commandLoop();
    }

    private boolean shouldStop(int line, int depth) {
        switch (mode) {
            case STEP:
                return true;
            case NEXT:
                return depth <= nextBaselineDepth;
            case CONTINUE:
                return breakpoints.contains(line);
            default:
                return true;
        }
    }

    private void printStop(int line, boolean atBreakpoint) {
        String text = sourceText(line);
        if (atBreakpoint) {
            out.println("[DEBUG] " + line + "번째 줄에서 정지 (breakpoint) → " + text);
        } else {
            out.println("[DEBUG] " + line + "번째 줄에서 정지 → " + text);
        }
    }

    private String sourceText(int line) {
        if (line >= 1 && line <= sourceLines.size()) {
            return sourceLines.get(line - 1).trim();
        }
        return "";
    }

    /** Read commands at the {@code >} prompt until a resume command is entered. */
    private void commandLoop() {
        try {
            while (true) {
                out.print("> ");
                out.flush();
                String line = in.readLine();
                if (line == null) {
                    // EOF on the command stream: resume so execution can finish.
                    return;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (dispatch(trimmed)) {
                    return; // a resume command was entered
                }
            }
        } catch (IOException e) {
            out.println("I/O error: " + e.getMessage());
        }
    }

    /** @return true if execution should resume. */
    private boolean dispatch(String input) {
        int space = input.indexOf(' ');
        String name = space < 0 ? input : input.substring(0, space);
        String argument = space < 0 ? "" : input.substring(space + 1).trim();
        DebugCommand command = commands.get(name);
        if (command == null) {
            out.println("[DEBUG] 알 수 없는 명령: " + name);
            return false;
        }
        return command.execute(this, argument);
    }

    // --- command registry (Command pattern) --------------------------------

    private void registerCommands() {
        commands.put("step", (dbg, arg) -> {
            dbg.mode = StepMode.STEP;
            return true;
        });
        commands.put("next", (dbg, arg) -> {
            // Remember the current depth; NEXT only re-pauses at this depth or
            // shallower, so we step over (not into) any block/function here.
            dbg.nextBaselineDepth = dbg.currentDepthForNext();
            dbg.mode = StepMode.NEXT;
            return true;
        });
        commands.put("continue", (dbg, arg) -> {
            dbg.mode = StepMode.CONTINUE;
            return true;
        });
        commands.put("break", (dbg, arg) -> {
            dbg.addBreakpoint(arg);
            return false;
        });
        commands.put("breakpoints", (dbg, arg) -> {
            dbg.listBreakpoints();
            return false;
        });
        commands.put("remove", (dbg, arg) -> {
            dbg.removeBreakpoint(arg);
            return false;
        });
        commands.put("watch", (dbg, arg) -> {
            dbg.addWatch(arg);
            return false;
        });
        commands.put("unwatch", (dbg, arg) -> {
            dbg.removeWatch(arg);
            return false;
        });
        commands.put("watches", (dbg, arg) -> {
            dbg.printWatchValues();
            return false;
        });
        commands.put("inspect", (dbg, arg) -> {
            dbg.inspect();
            return false;
        });
    }

    // The depth passed to beforeStmt is the depth at which the *current* (paused)
    // statement runs. NEXT must re-pause at the same level, so the baseline is
    // exactly that current depth, captured from the most recent beforeStmt.
    private int currentDepthForNext() {
        return lastDepth;
    }

    // --- breakpoint commands ----------------------------------------------

    void addBreakpoint(String arg) {
        Integer line = parseLine(arg);
        if (line == null) return;
        breakpoints.add(line);
        out.println("[DEBUG] " + line + "번째 줄에 breakpoint 설정");
    }

    void removeBreakpoint(String arg) {
        Integer line = parseLine(arg);
        if (line == null) return;
        if (breakpoints.remove(line)) {
            out.println("[DEBUG] " + line + "번째 줄 breakpoint 해제");
        } else {
            out.println("[DEBUG] " + line + "번째 줄에 breakpoint 없음");
        }
    }

    void listBreakpoints() {
        if (breakpoints.isEmpty()) {
            out.println("[DEBUG] breakpoint 없음");
            return;
        }
        StringBuilder sb = new StringBuilder("[DEBUG] breakpoints:");
        for (int line : breakpoints) {
            sb.append(' ').append(line);
        }
        out.println(sb.toString());
    }

    private Integer parseLine(String arg) {
        try {
            return Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            out.println("[DEBUG] 줄번호가 필요합니다: '" + arg + "'");
            return null;
        }
    }

    // --- watch commands ----------------------------------------------------

    void addWatch(String name) {
        if (name.isEmpty()) {
            out.println("[DEBUG] 변수명이 필요합니다");
            return;
        }
        watches.add(name);
        out.println("[WATCH] '" + name + "' 감시 등록");
    }

    void removeWatch(String name) {
        if (watches.remove(name)) {
            out.println("[WATCH] '" + name + "' 감시 해제");
        }
    }

    /** Print each watched variable's value from the nearest enclosing scope. */
    void printWatchValues() {
        for (String name : watches) {
            Object value = lookup(name);
            out.println("[WATCH] " + name + " = " + Executor.stringify(value));
        }
    }

    /** Find {@code name} by walking from the current scope outward; null if none. */
    private Object lookup(String name) {
        Environment env = currentEnv;
        while (env != null) {
            Map<String, Object> bindings = env.bindings();
            if (bindings.containsKey(name)) {
                return bindings.get(name);
            }
            env = env.enclosing();
        }
        return null;
    }

    // --- inspect -----------------------------------------------------------

    /**
     * Print every visible variable, walking the scope chain from the current
     * scope to the global one. Variables in the global scope are labelled
     * {@code [전역]}, all others {@code [로컬]}, each with value and type. A name
     * shadowed in an inner scope is shown only once (the nearest binding wins).
     */
    void inspect() {
        out.println("[DEBUG] 현재 스코프 변수:");
        Set<String> seen = new LinkedHashSet<>();
        Environment env = currentEnv;
        while (env != null) {
            boolean isGlobal = env.enclosing() == null;
            String label = isGlobal ? "[전역]" : "[로컬]";
            for (Map.Entry<String, Object> entry : env.bindings().entrySet()) {
                String name = entry.getKey();
                if (!seen.add(name)) {
                    continue; // a nearer scope already reported this name
                }
                Object value = entry.getValue();
                out.println(label + " " + name + " = " + Executor.stringify(value)
                        + " (" + typeName(value) + ")");
            }
            env = env.enclosing();
        }
    }

    /** CodeFab type name for a runtime value (contract §10-2 labels). */
    static String typeName(Object value) {
        if (value == null) return "Nil";
        if (value instanceof Double) return "Number";
        if (value instanceof Boolean) return "Boolean";
        if (value instanceof String) return "String";
        if (value instanceof CodeFabArray) return "Array";
        if (value instanceof CodeFabCallable) return "Function";
        return value.getClass().getSimpleName();
    }
}
