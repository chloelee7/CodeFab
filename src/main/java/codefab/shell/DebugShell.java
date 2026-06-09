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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DebugShell {

    private final BufferedReader in;
    private final PrintStream out;
    private final PrintStream err;
    private final String filePath;

    private List<Stmt> statements;
    private int cursor = 0;
    private final Set<Integer> breakpoints = new HashSet<>();
    private final Set<String> watchList = new LinkedHashSet<>();
    private boolean stoppedAtBreakpoint = false;

    private CollectingOutputSink outputSink;
    private Executor executor;

    private final Map<String, DebugCommand> commands = new HashMap<>();

    public DebugShell(BufferedReader in, PrintStream out, PrintStream err, String filePath) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.filePath = filePath;
        registerCommands();
    }

    private void registerCommands() {
        commands.put("step", (s, a) -> s.doStep());
        commands.put("next", (s, a) -> s.doStep());
        commands.put("continue", (s, a) -> s.doContinue());
        commands.put("break", (s, a) -> {
            s.doBreak(a);
            return true;
        });
        commands.put("breakpoints", (s, a) -> {
            s.doListBreakpoints();
            return true;
        });
        commands.put("remove", (s, a) -> {
            s.doRemoveBreakpoint(a);
            return true;
        });
        commands.put("watch", (s, a) -> {
            if (a.isEmpty()) {
                s.out.println("Usage: watch <variable>");
                return true;
            }
            s.doWatch(a);
            return true;
        });
        commands.put("unwatch", (s, a) -> {
            if (a.isEmpty()) {
                s.out.println("Usage: unwatch <variable>");
                return true;
            }
            s.doUnwatch(a);
            return true;
        });
        commands.put("watches", (s, a) -> {
            s.doWatches();
            return true;
        });
        commands.put("inspect", (s, a) -> {
            s.doInspect();
            return true;
        });
        commands.put("exit", (s, a) -> false);
        commands.put("quit", (s, a) -> false);
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
        this.executor = new Executor(outputSink, new Environment());

        out.println("[DEBUG] 소스코드 로딩: " + filePath);
        printCurrentStmt();

        try {
            while (cursor < statements.size()) {
                out.print("> ");
                out.flush();
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                String cmd = line.trim();
                if (!handleCommand(cmd)) {
                    break;
                }
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
                out.println("[DEBUG] " + line + "번째 줄에서 정지 (breakpoint) → " + stmtText(
                    statements.get(cursor)));
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
        if (watchList.isEmpty()) {
            return;
        }
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
            return getExprLine(s.expression());
        }
        if (stmt instanceof Stmt.ExpressionStmt s) {
            return getExprLine(s.expression());
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

    private int findBreakpointLine(Stmt stmt) {
        int line = getLine(stmt);
        if (line > 0 && breakpoints.contains(line)) {
            return line;
        }

        if (stmt instanceof Stmt.BlockStmt s) {
            return firstBreakpointLine(s.statements());
        }

        return -1;
    }

    private int firstBreakpointLine(List<Stmt> candidates) {
        for (Stmt candidate : candidates) {
            int line = findBreakpointLine(candidate);
            if (line > 0) {
                return line;
            }
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
