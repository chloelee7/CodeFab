package codefab.shell;

import codefab.CodeFabSession;
import codefab.CollectingOutputSink;
import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.executor.Executor;
import codefab.executor.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
    private boolean stoppedAtBreakpoint = false;

    private CollectingOutputSink outputSink;
    private Executor executor;

    public DebugShell(BufferedReader in, PrintStream out, String filePath) {
        this.in = in;
        this.out = out;
        this.filePath = filePath;
    }

    public void run() {
        // 파일 로드
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
        if (cmd.equals("step") || cmd.equals("next")) {
            return doStep();
        }
        if (cmd.startsWith("break ")) {
            doBreak(cmd.substring(6).trim());
            return true;
        }
        if (cmd.equals("breakpoints")) {
            doListBreakpoints();
            return true;
        }
        if (cmd.startsWith("remove ")) {
            doRemoveBreakpoint(cmd.substring(7).trim());
            return true;
        }
        if (cmd.equals("continue")) {
            return doContinue();
        }
        if (cmd.startsWith("watch ")) {
            doWatch(cmd.substring(6).trim());
            return true;
        }
        if (cmd.startsWith("unwatch ")) {
            doUnwatch(cmd.substring(8).trim());
            return true;
        }
        if (cmd.equals("watches")) {
            doWatches();
            return true;
        }
        if (cmd.equals("inspect")) {
            doInspect();
            return true;
        }
        if (cmd.equals("exit") || cmd.equals("quit")) {
            return false;
        }
        out.println("Unknown command: " + cmd);
        return true;
    }

    // ── stepping ───────────────────────────────────────────────────────────────

    private boolean doStep() {
        if (cursor >= statements.size()) {
            out.println("[DEBUG] 더 이상 실행할 구문이 없습니다.");
            return false;
        }
        executeOne(statements.get(cursor));
        cursor++;
        stoppedAtBreakpoint = false;
        printWatches();
        if (cursor < statements.size()) {
            printCurrentStmt();
        }
        return true;
    }

    private boolean doContinue() {
        while (cursor < statements.size()) {
            int line = findBreakpointLine(statements.get(cursor));
            if (!stoppedAtBreakpoint && line > 0) {
                out.println("[DEBUG] " + line + "번째 줄에서 정지 (breakpoint) → " + stmtText(statements.get(cursor)));
                printWatches();
                stoppedAtBreakpoint = true;
                return true;
            }
            executeOne(statements.get(cursor));
            cursor++;
            stoppedAtBreakpoint = false;
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

    // ── breakpoints ────────────────────────────────────────────────────────────

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

    // ── watch ─────────────────────────────────────────────────────────────────

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

    // ── helpers ────────────────────────────────────────────────────────────────

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
        if (stmt instanceof Stmt.VarStmt s)        return s.name.line;
        if (stmt instanceof Stmt.FunctionStmt s)   return s.name.line;
        if (stmt instanceof Stmt.ReturnStmt s)     return s.keyword.line;
        if (stmt instanceof Stmt.PrintStmt s)      return getExprLine(s.expression);
        if (stmt instanceof Stmt.ExpressionStmt s) return getExprLine(s.expression);
        if (stmt instanceof Stmt.IfStmt s)         return getExprLine(s.condition);
        if (stmt instanceof Stmt.WhileStmt s)      return getExprLine(s.condition);
        if (stmt instanceof Stmt.ForStmt f) {
            int line = f.initializer != null ? getLine(f.initializer) : -1;
            if (line > 0) return line;
            line = f.condition != null ? getExprLine(f.condition) : -1;
            if (line > 0) return line;
            return f.increment != null ? getExprLine(f.increment) : -1;
        }
        return -1;
    }

    private int getExprLine(Expr expr) {
        if (expr instanceof Expr.Variable e) return e.name.line;
        if (expr instanceof Expr.Assign e)   return e.name.line;
        if (expr instanceof Expr.Unary e)    return e.operator.line;
        if (expr instanceof Expr.Binary e)   return e.operator.line;
        if (expr instanceof Expr.Logical e)  return e.operator.line;
        if (expr instanceof Expr.Grouping e) return getExprLine(e.expression);
        if (expr instanceof Expr.Call e)     return e.paren.line;
        if (expr instanceof Expr.ArrayGet e) return e.bracket.line;
        if (expr instanceof Expr.ArraySet e) return e.bracket.line;
        return -1;
    }

    private int findBreakpointLine(Stmt stmt) {
        int line = getLine(stmt);
        if (line > 0 && breakpoints.contains(line)) return line;

        if (stmt instanceof Stmt.BlockStmt s) {
            return firstBreakpointLine(s.statements);
        }

        return -1;
    }

    private int firstBreakpointLine(List<Stmt> candidates) {
        for (Stmt candidate : candidates) {
            int line = findBreakpointLine(candidate);
            if (line > 0) return line;
        }
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
