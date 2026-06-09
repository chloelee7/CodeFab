package codefab.shell;

import codefab.CodeFabSession;
import codefab.CollectingOutputSink;
import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.executor.Environment;
import codefab.executor.Executor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DebugShell {

    /** 디버거를 끝내기 위한 내부 제어 흐름 신호 (exit/quit/EOF). */
    private static final class DebugExit extends RuntimeException {
        DebugExit() {
            super(null, null, false, false);
        }
    }

    private final LineSource lineSource;
    private final PrintStream out;
    private final PrintStream err;
    private final String filePath;

    private List<Stmt> statements;
    private final Set<Integer> breakpoints = new HashSet<>();
    private final Set<String> watchList = new LinkedHashSet<>();

    /**
     * STEP_INTO(step): 다음에 실행되는 statement에서 정지 (블록 내부 진입).
     * STEP_OVER(next): 현재 statement를 끝까지 실행하되 내부로 진입하지 않고
     *                  같은/상위 레벨(정지 시점 depth 이하)의 다음 statement에서 정지.
     * CONTINUE: 다음 breakpoint까지 진행.
     */
    private enum Mode { STEP_INTO, STEP_OVER, CONTINUE }

    private Mode mode = Mode.STEP_INTO;   // 초기: 첫 statement에서 정지
    private int stepOverDepth = -1;       // STEP_OVER 기준 깊이
    private int currentDepth = 0;         // 현재 정지한 statement의 깊이

    private CollectingOutputSink outputSink;
    private Executor executor;

    /** 기존 생성자 — BufferedReader 경로로 위임(테스트 무변경). */
    public DebugShell(BufferedReader in, PrintStream out, PrintStream err, String filePath) {
        this(new BufferedLineSource(in, out), out, err, filePath);
    }

    public DebugShell(LineSource lineSource, PrintStream out, PrintStream err, String filePath) {
        this.lineSource = lineSource;
        this.out = out;
        this.err = err;
        this.filePath = filePath;
    }

    public void run() {
        String source;
        try {
            source = ShellFiles.readUtf8(filePath);
        } catch (IOException e) {
            ShellFiles.printReadError(err, filePath);
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
        this.executor = new Executor(outputSink, Executor.newGlobalScope());
        // statement 단위로 끼어들어 step/breakpoint를 처리한다. 중첩 블록(함수 본문,
        // for/while 본문, if 분기) 내부의 statement도 이 훅을 통과하므로 블록 내부에서 멈춘다.
        executor.setDebugHook(this::onStatement);

        out.println("[DEBUG] 소스코드 로딩: " + filePath);

        try {
            executor.execute(statements);
        } catch (DebugExit e) {
            // 사용자가 exit/quit 했거나 입력이 끝남 — 정상 종료
        } catch (codefab.core.InterpreterRuntimeError e) {
            // 사용자 코드의 런타임 오류만 여기서 보고한다.
            drainOutput();
            out.println("[RUNTIME ERROR] " + e.getMessage());
        }
        // 그 외 RuntimeException/Error는 디버거 자체 버그이므로 가리지 않고 전파한다.
        // (top-level return은 Checker가 금지하므로 ReturnException은 여기까지 오지 않는다.)

        drainOutput();
        out.println("[DEBUG] 실행 완료.");
    }

    /** Executor가 각 statement 실행 직전에 호출. 멈춰야 하면 대화형 명령 루프로 진입한다. */
    private void onStatement(Stmt stmt, int depth) {
        // BlockStmt 자체는 줄번호가 없고(정지 메시지가 줄 미상이 됨) breakpoint 대상도 아니다.
        // 통과시키면 내부 각 statement에서 정상적으로 멈추므로 블록 노드 이중 정지를 피한다.
        if (stmt instanceof Stmt.BlockStmt) {
            return;
        }
        int line = getLine(stmt);
        boolean atBreakpoint = line > 0 && breakpoints.contains(line);
        // breakpoint는 모드와 무관하게 항상 정지 (step-over 중 함수 본문 내부 breakpoint 포함).
        boolean pause = atBreakpoint
                || mode == Mode.STEP_INTO
                || (mode == Mode.STEP_OVER && depth <= stepOverDepth);
        if (!pause) {
            return;
        }
        currentDepth = depth;

        // 직전까지 실행된 statement들이 만든 출력을 먼저 비운다.
        drainOutput();

        if (atBreakpoint) {
            out.println("[DEBUG] " + line + "번째 줄에서 정지 (breakpoint) → " + stmtText(stmt));
        } else if (line > 0) {
            out.println("[DEBUG] " + line + "번째 줄에서 정지 → " + stmtText(stmt));
        } else {
            out.println("[DEBUG] 정지 → " + stmtText(stmt));
        }
        printWatches();
        interact();
    }

    /** step/continue로 실행을 재개할 때까지 명령을 읽어 처리한다. exit/quit/EOF는 DebugExit를 던진다. */
    private void interact() {
        while (true) {
            String raw;
            try {
                raw = lineSource.readLine("> ");
            } catch (IOException e) {
                throw new DebugExit();
            }
            if (raw == null) {
                throw new DebugExit();
            }
            String cmd = raw.trim();
            int space = cmd.indexOf(' ');
            String name = space < 0 ? cmd : cmd.substring(0, space);
            String arg = space < 0 ? "" : cmd.substring(space + 1).trim();

            switch (name) {
                case "step":
                    mode = Mode.STEP_INTO;
                    return;
                case "next":
                    mode = Mode.STEP_OVER;
                    stepOverDepth = currentDepth; // 현재 깊이 이하로 복귀할 때 정지
                    return;
                case "continue":
                    mode = Mode.CONTINUE;
                    return;
                case "break":
                    doBreak(arg);
                    break;
                case "breakpoints":
                    doListBreakpoints();
                    break;
                case "remove":
                    doRemoveBreakpoint(arg);
                    break;
                case "watch":
                    if (arg.isEmpty()) {
                        out.println("Usage: watch <variable>");
                    } else {
                        doWatch(arg);
                    }
                    break;
                case "unwatch":
                    if (arg.isEmpty()) {
                        out.println("Usage: unwatch <variable>");
                    } else {
                        doUnwatch(arg);
                    }
                    break;
                case "watches":
                    doWatches();
                    break;
                case "inspect":
                    doInspect();
                    break;
                case "exit":
                case "quit":
                    throw new DebugExit();
                case "":
                    break;
                default:
                    out.println("Unknown command: " + cmd);
            }
        }
    }

    private void drainOutput() {
        for (String o : outputSink.lines()) {
            out.println(o);
        }
        outputSink.clear();
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
        printWatchValues();
    }

    private void printWatches() {
        if (watchList.isEmpty()) {
            return;
        }
        printWatchValues();
    }

    private void printWatchValues() {
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

    private String lookupVar(Environment env, String name) {
        try {
            Token tok = new Token(codefab.core.TokenType.IDENTIFIER, name, null, 0);
            return Executor.stringify(env.get(tok));
        } catch (Exception e) {
            return "(undefined)";
        }
    }

    private int getLine(Stmt stmt) {
        if (stmt instanceof Stmt.VarStmt s) {
            return s.name().line;
        }
        if (stmt instanceof Stmt.FunctionStmt s) {
            return s.name().line;
        }
        if (stmt instanceof Stmt.ReturnStmt s) {
            return s.keyword().line;
        }
        if (stmt instanceof Stmt.PrintStmt s) {
            return s.line() > 0 ? s.line() : getExprLine(s.expression());
        }
        if (stmt instanceof Stmt.ExpressionStmt s) {
            return s.line() > 0 ? s.line() : getExprLine(s.expression());
        }
        if (stmt instanceof Stmt.IfStmt s) {
            return getExprLine(s.condition());
        }
        if (stmt instanceof Stmt.WhileStmt s) {
            return getExprLine(s.condition());
        }
        if (stmt instanceof Stmt.ForStmt f) {
            int line = f.initializer() != null ? getLine(f.initializer()) : -1;
            if (line > 0) {
                return line;
            }
            line = f.condition() != null ? getExprLine(f.condition()) : -1;
            if (line > 0) {
                return line;
            }
            return f.increment() != null ? getExprLine(f.increment()) : -1;
        }
        return -1;
    }

    private int getExprLine(Expr expr) {
        if (expr instanceof Expr.Variable e) {
            return e.name.line;
        }
        if (expr instanceof Expr.Assign e) {
            return e.name.line;
        }
        if (expr instanceof Expr.Unary e) {
            return e.operator().line;
        }
        if (expr instanceof Expr.Binary e) {
            return e.operator().line;
        }
        if (expr instanceof Expr.Logical e) {
            return e.operator().line;
        }
        if (expr instanceof Expr.Grouping e) {
            return getExprLine(e.expression());
        }
        if (expr instanceof Expr.Call e) {
            return e.paren().line;
        }
        if (expr instanceof Expr.ArrayGet e) {
            return e.bracket().line;
        }
        if (expr instanceof Expr.ArraySet e) {
            return e.bracket().line;
        }
        return -1;
    }

    private String stmtText(Stmt stmt) {
        if (stmt instanceof Stmt.VarStmt s) {
            String init = s.initializer() != null ? " = " + exprText(s.initializer()) : "";
            return "var " + s.name().lexeme + init + ";";
        }
        if (stmt instanceof Stmt.ExpressionStmt s) return exprText(s.expression()) + ";";
        if (stmt instanceof Stmt.PrintStmt s)      return "print " + exprText(s.expression()) + ";";
        if (stmt instanceof Stmt.ReturnStmt s)     return s.value() != null ? "return " + exprText(s.value()) + ";" : "return;";
        if (stmt instanceof Stmt.IfStmt s) {
            String els = s.elseBranch() != null ? " else " + bodyText(s.elseBranch()) : "";
            return "if (" + exprText(s.condition()) + ") " + bodyText(s.thenBranch()) + els;
        }
        if (stmt instanceof Stmt.WhileStmt s)      return "while (" + exprText(s.condition()) + ") " + bodyText(s.body());
        if (stmt instanceof Stmt.ForStmt s) {
            String init = s.initializer() != null ? stmtText(s.initializer()).replaceAll(";$", "") : "";
            String cond = s.condition()  != null ? exprText(s.condition())  : "";
            String incr = s.increment()  != null ? exprText(s.increment())  : "";
            return "for (" + init + "; " + cond + "; " + incr + ") " + bodyText(s.body());
        }
        if (stmt instanceof Stmt.FunctionStmt s) {
            String params = String.join(", ", s.params().stream().map(t -> t.lexeme).toList());
            String body = s.body().stream().map(this::stmtText).collect(java.util.stream.Collectors.joining(" "));
            return "Func " + s.name().lexeme + "(" + params + ") { " + body + " }";
        }
        if (stmt instanceof Stmt.BlockStmt s)      return bodyText(s);
        return stmt.getClass().getSimpleName();
    }

    private String bodyText(Stmt body) {
        if (body instanceof Stmt.BlockStmt b) {
            String inner = b.statements().stream().map(this::stmtText).collect(java.util.stream.Collectors.joining(" "));
            return "{ " + inner + " }";
        }
        return stmtText(body);
    }

    private String exprText(Expr expr) {
        if (expr instanceof Expr.Literal e) {
            if (e.value() == null)             return "nil";
            if (e.value() instanceof Boolean)  return e.value().toString();
            if (e.value() instanceof Double d) return d == Math.floor(d) && !Double.isInfinite(d)
                                                    ? String.valueOf(d.longValue()) : d.toString();
            return "\"" + e.value() + "\"";
        }
        if (expr instanceof Expr.Variable e)  return e.name.lexeme;
        if (expr instanceof Expr.Assign e)    return e.name.lexeme + " = " + exprText(e.value);
        if (expr instanceof Expr.Unary e)     return e.operator().lexeme + exprText(e.right());
        if (expr instanceof Expr.Binary e)    return exprText(e.left()) + " " + e.operator().lexeme + " " + exprText(e.right());
        if (expr instanceof Expr.Logical e)   return exprText(e.left()) + " " + e.operator().lexeme + " " + exprText(e.right());
        if (expr instanceof Expr.Grouping e)  return "(" + exprText(e.expression()) + ")";
        if (expr instanceof Expr.Call e) {
            String args = e.arguments().stream().map(this::exprText).reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b);
            return exprText(e.callee()) + "(" + args + ")";
        }
        if (expr instanceof Expr.ArrayGet e)  return exprText(e.array()) + "[" + exprText(e.index()) + "]";
        if (expr instanceof Expr.ArraySet e)  return exprText(e.array()) + "[" + exprText(e.index()) + "] = " + exprText(e.value());
        return "?";
    }
}
