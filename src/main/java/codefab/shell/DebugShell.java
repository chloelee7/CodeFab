package codefab.shell;

import codefab.CodeFabSession;
import codefab.core.Diagnostic;
import codefab.core.Stmt;
import java.util.ArrayList;
import codefab.core.Token;
import codefab.executor.Environment;
import codefab.executor.Executor;
import codefab.CollectingOutputSink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 단계별 실행을 지원하는 디버그 셸.
 * Stmt 단위로 커서를 이동하며 step/break/continue/watch/inspect를 제공한다.
 */
public final class DebugShell {

    private final BufferedReader in;
    private final PrintStream out;
    private final String filePath;

    private List<Stmt> statements;
    private int cursor = 0;
    private final Set<Integer> breakpoints = new HashSet<>();
    private final Set<String> watchList = new LinkedHashSet<>();

    private CollectingOutputSink outputSink;
    private Executor executor;

    /** 명령 이름 → 명령(GoF Command). 첫 토큰으로 디스패치. */
    private final Map<String, DebugCommand> commands = new HashMap<>();

    public DebugShell(BufferedReader in, PrintStream out, String filePath) {
        this.in = in;
        this.out = out;
        this.filePath = filePath;
        registerCommands();
    }

    private void registerCommands() {
        commands.put("step",        (s, a) -> s.doStep());
        commands.put("next",        (s, a) -> s.doStep());
        commands.put("continue",    (s, a) -> s.doContinue());
        commands.put("break",       (s, a) -> { s.doBreak(a); return true; });
        commands.put("breakpoints", (s, a) -> { s.doListBreakpoints(); return true; });
        commands.put("remove",      (s, a) -> { s.doRemoveBreakpoint(a); return true; });
        commands.put("watch",       (s, a) -> {
            if (a.isEmpty()) { s.out.println("Usage: watch <variable>"); return true; }
            s.doWatch(a); return true;
        });
        commands.put("unwatch",     (s, a) -> {
            if (a.isEmpty()) { s.out.println("Usage: unwatch <variable>"); return true; }
            s.doUnwatch(a); return true;
        });
        commands.put("watches",     (s, a) -> { s.doWatches(); return true; });
        commands.put("inspect",     (s, a) -> { s.doInspect(); return true; });
        commands.put("exit",        (s, a) -> false);
        commands.put("quit",        (s, a) -> false);
    }

    public void run() {
        String source;
        try {
            source = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("Error: file not found: " + filePath);
            return;
        }

        // 파싱 + Checker + ConstantFolder (진단 있으면 중단)
        CodeFabSession session = new CodeFabSession();
        List<Diagnostic> diagnostics = new ArrayList<>();
        this.statements = session.getStatements(source, diagnostics);
        if (!diagnostics.isEmpty()) {
            for (Diagnostic d : diagnostics) {
                out.println(d.render());
            }
            return;
        }
        if (statements.isEmpty()) {
            out.println("[DEBUG] 실행할 구문이 없습니다.");
            return;
        }

        this.outputSink = new CollectingOutputSink();
        this.executor = new Executor(outputSink, new Environment());

        out.println("[DEBUG] 소스코드 로딩: " + filePath);
        printCurrentStmt();

        try {
            while (cursor < statements.size()) {
                out.print("> ");
                out.flush();
                String line = in.readLine();
                if (line == null) break;
                String cmd = line.trim();
                if (!handleCommand(cmd)) break;
            }
        } catch (IOException e) {
            out.println("I/O error: " + e.getMessage());
        }

        out.println("[DEBUG] 실행 완료.");
    }

    private boolean handleCommand(String cmd) {
        int space = cmd.indexOf(' ');
        String name = space < 0 ? cmd : cmd.substring(0, space);
        String arg = space < 0 ? "" : cmd.substring(space + 1).trim();

        DebugCommand command = commands.get(name);
        if (command == null) {
            out.println("Unknown command: " + cmd);
            return true;
        }
        return command.execute(this, arg);
    }

    private boolean doStep() {
        if (cursor >= statements.size()) {
            out.println("[DEBUG] 더 이상 실행할 구문이 없습니다.");
            return false;
        }
        executeOne(statements.get(cursor));
        cursor++;
        printWatches();
        if (cursor < statements.size()) {
            printCurrentStmt();
        }
        return true;
    }

    private boolean doContinue() {
        while (cursor < statements.size()) {
            int line = getLine(statements.get(cursor));
            if (breakpoints.contains(line)) {
                out.println("[DEBUG] " + line + "번째 줄에서 정지 (breakpoint) → " + stmtText(statements.get(cursor)));
                printWatches();
                cursor++;
                return true;
            }
            executeOne(statements.get(cursor));
            cursor++;
            printWatches();
        }
        return false;
    }

    private void executeOne(Stmt stmt) {
        outputSink.clear();
        try {
            executor.execute(List.of(stmt));
        } catch (codefab.core.InterpreterRuntimeError e) {
            out.println("[RUNTIME ERROR] " + e.getMessage());
        } catch (RuntimeException e) {
            // ReturnException(package-private) 포함 — top-level return 등 비정상 흐름 처리
            String msg = e.getMessage();
            out.println("[RUNTIME ERROR] " + (msg != null ? msg : e.getClass().getSimpleName()));
        }
        for (String o : outputSink.lines()) {
            out.println(o);
        }
    }

    private void doBreak(String arg) {
        try {
            int line = Integer.parseInt(arg);
            breakpoints.add(line);
            out.println("[DEBUG] " + line + "번째 줄에 breakpoint 설정");
        } catch (NumberFormatException e) {
            out.println("Usage: break <line>");
        }
    }

    private void doListBreakpoints() {
        if (breakpoints.isEmpty()) {
            out.println("[DEBUG] 설정된 breakpoint 없음");
        } else {
            out.println("[DEBUG] Breakpoints: " + breakpoints);
        }
    }

    private void doRemoveBreakpoint(String arg) {
        try {
            int line = Integer.parseInt(arg);
            if (breakpoints.remove(line)) {
                out.println("[DEBUG] " + line + "번째 줄 breakpoint 해제");
            } else {
                out.println("[DEBUG] " + line + "번째 줄에 breakpoint 없음");
            }
        } catch (NumberFormatException e) {
            out.println("Usage: remove <line>");
        }
    }

    private void doWatch(String varName) {
        watchList.add(varName);
        out.println("[WATCH] '" + varName + "' 감시 등록");
    }

    private void doUnwatch(String varName) {
        watchList.remove(varName);
        out.println("[WATCH] '" + varName + "' 감시 해제");
    }

    private void doWatches() {
        if (watchList.isEmpty()) {
            out.println("[WATCH] 감시 중인 변수 없음");
            return;
        }
        Environment env = executor.getEnvironment();
        for (String name : watchList) {
            String val = lookupVar(env, name);
            out.println("[WATCH] " + name + " = " + val);
        }
    }

    private void printWatches() {
        if (watchList.isEmpty()) return;
        Environment env = executor.getEnvironment();
        for (String name : watchList) {
            String val = lookupVar(env, name);
            out.println("[WATCH] " + name + " = " + val);
        }
    }

    private void doInspect() {
        Environment env = executor.getEnvironment();
        out.println("—— 현재 스코프 변수 ——————————————————");
        List<Environment.VarInfo> vars = env.dumpAll();
        if (vars.isEmpty()) {
            out.println("(변수 없음)");
            return;
        }
        for (Environment.VarInfo info : vars) {
            String scope = info.isLocal ? "[로컬]" : "[전역]";
            out.println(scope + " " + info.name + " = "
                    + Executor.stringify(info.value) + " (" + info.typeName() + ")");
        }
    }

    private void printCurrentStmt() {
        if (cursor < statements.size()) {
            int line = getLine(statements.get(cursor));
            String text = stmtText(statements.get(cursor));
            if (line > 0) {
                out.println("[DEBUG] " + line + "번째 줄에서 정지 → " + text);
            } else {
                out.println("[DEBUG] 정지 → " + text);
            }
        }
    }

    private String lookupVar(Environment env, String name) {
        try {
            Token tok = new Token(codefab.core.TokenType.IDENTIFIER, name, null, 0);
            return Executor.stringify(env.get(tok));
        } catch (Exception e) {
            return "(undefined)";
        }
    }

    private int getLine(Stmt stmt) {
        if (stmt instanceof Stmt.VarStmt)        return ((Stmt.VarStmt) stmt).name.line;
        if (stmt instanceof Stmt.FunctionStmt)   return ((Stmt.FunctionStmt) stmt).name.line;
        if (stmt instanceof Stmt.ReturnStmt)     return ((Stmt.ReturnStmt) stmt).keyword.line;
        if (stmt instanceof Stmt.PrintStmt) {
            Stmt.PrintStmt p = (Stmt.PrintStmt) stmt;
            return getExprLine(p.expression);
        }
        if (stmt instanceof Stmt.ExpressionStmt) {
            return getExprLine(((Stmt.ExpressionStmt) stmt).expression);
        }
        if (stmt instanceof Stmt.IfStmt)         return getExprLine(((Stmt.IfStmt) stmt).condition);
        if (stmt instanceof Stmt.WhileStmt)      return getExprLine(((Stmt.WhileStmt) stmt).condition);
        return -1;
    }

    private int getExprLine(codefab.core.Expr expr) {
        if (expr instanceof codefab.core.Expr.Variable)
            return ((codefab.core.Expr.Variable) expr).name.line;
        if (expr instanceof codefab.core.Expr.Assign)
            return ((codefab.core.Expr.Assign) expr).name.line;
        if (expr instanceof codefab.core.Expr.Binary)
            return ((codefab.core.Expr.Binary) expr).operator.line;
        if (expr instanceof codefab.core.Expr.Call)
            return ((codefab.core.Expr.Call) expr).paren.line;
        return -1;
    }

    private String stmtText(Stmt stmt) {
        if (stmt instanceof Stmt.VarStmt)        return "var " + ((Stmt.VarStmt) stmt).name.lexeme + " = ...;";
        if (stmt instanceof Stmt.FunctionStmt)   return "Func " + ((Stmt.FunctionStmt) stmt).name.lexeme + "(...) { ... }";
        if (stmt instanceof Stmt.ReturnStmt)     return "return ...;";
        if (stmt instanceof Stmt.PrintStmt)      return "print ...;";
        if (stmt instanceof Stmt.IfStmt)         return "if (...) { ... }";
        if (stmt instanceof Stmt.WhileStmt)      return "while (...) { ... }";
        if (stmt instanceof Stmt.ForStmt)        return "for (...) { ... }";
        if (stmt instanceof Stmt.BlockStmt)      return "{ ... }";
        return stmt.getClass().getSimpleName();
    }
}
