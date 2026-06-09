package codefab.shell;

import codefab.CollectingOutputSink;
import codefab.RunResult;
import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.Checker;
import codefab.checker.ConstantFolder;
import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.InterpreterRuntimeError;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.executor.Environment;
import codefab.executor.Executor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ExplainMode implements Mode {

    private static final int EX_NO_INPUT = 66;

    private final String path;

    public ExplainMode(String path) {
        this.path = path;
    }

    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        String source;
        try {
            source = ShellFiles.readUtf8(path);
        } catch (IOException e) {
            ShellFiles.printReadError(err, path);
            return EX_NO_INPUT;
        }

        List<Diagnostic> scannerDiagnostics = new ArrayList<>();
        List<Token> tokens = new Scanner(source, scannerDiagnostics).scanTokens();
        printHeader(out, "Scanner Tokens");
        for (Token token : tokens) {
            out.println(token.line + ": " + token);
        }
        printHeader(out, "Scanner Diagnostics");
        printDiagnostics(out, scannerDiagnostics);

        List<Diagnostic> parserDiagnostics = new ArrayList<>();
        List<Stmt> statements = new Parser(tokens, parserDiagnostics).parse();
        printHeader(out, "Parser AST");
        printStatements(out, statements);
        printHeader(out, "Parser Diagnostics");
        printDiagnostics(out, parserDiagnostics);

        printHeader(out, "Checker Diagnostics");
        if (!scannerDiagnostics.isEmpty() || !parserDiagnostics.isEmpty()) {
            String reason = assembleDiagnosticsReason(scannerDiagnostics, parserDiagnostics);
            out.println("(skipped: " + reason + ")");
            printSkippedRemainder(out, reason);
            return 0;
        }

        List<Diagnostic> checkerDiagnostics = new ArrayList<>();
        new Checker(checkerDiagnostics).check(statements);
        printDiagnostics(out, checkerDiagnostics);
        if (!checkerDiagnostics.isEmpty()) {
            printSkippedRemainder(out, "checker diagnostics");
            return 0;
        }

        List<Stmt> folded = new ConstantFolder().fold(statements);
        printHeader(out, "Constant-Folded AST");
        printStatements(out, folded);

        printHeader(out, "Executor Result");
        printRunResult(out, executeFolded(folded));
        return 0;
    }

    private static String assembleDiagnosticsReason(
        List<Diagnostic> scannerDiagnostics,
        List<Diagnostic> parserDiagnostics
    ) {
        if (!scannerDiagnostics.isEmpty() && !parserDiagnostics.isEmpty()) {
            return "scanner/parser diagnostics";
        }
        if (!scannerDiagnostics.isEmpty()) {
            return "scanner diagnostics";
        }
        return "parser diagnostics";
    }

    private static RunResult executeFolded(List<Stmt> folded) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        CollectingOutputSink output = new CollectingOutputSink();
        Executor executor = new Executor(output, Executor.newGlobalScope());
        try {
            executor.execute(folded);
        } catch (InterpreterRuntimeError error) {
            diagnostics.add(new Diagnostic(Diagnostic.Stage.RUNTIME, error.line(), error.getMessage()));
            return new RunResult(false, output.lines(), diagnostics);
        }
        return new RunResult(true, output.lines(), diagnostics);
    }

    private static void printSkippedRemainder(PrintStream out, String reason) {
        printHeader(out, "Constant-Folded AST");
        out.println("(skipped: " + reason + ")");
        printHeader(out, "Executor Result");
        out.println("(skipped: " + reason + ")");
    }

    private static void printHeader(PrintStream out, String title) {
        out.println("== " + title + " ==");
    }

    private static void printStatements(PrintStream out, List<Stmt> statements) {
        if (statements.isEmpty()) {
            out.println("(none)");
            return;
        }
        AstFormatter formatter = new AstFormatter();
        for (Stmt statement : statements) {
            out.println(formatter.format(statement));
        }
    }

    private static void printDiagnostics(PrintStream out, List<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            out.println("(none)");
            return;
        }
        for (Diagnostic diagnostic : diagnostics) {
            out.println(diagnostic.render());
        }
    }

    private static void printRunResult(PrintStream out, RunResult result) {
        out.println("Success: " + result.success());
        out.println("Output:");
        printLines(out, result.output());
        out.println("Diagnostics:");
        printDiagnostics(out, result.diagnostics());
    }

    private static void printLines(PrintStream out, List<String> lines) {
        if (lines.isEmpty()) {
            out.println("(none)");
            return;
        }
        for (String line : lines) {
            out.println(line);
        }
    }

    private static final class AstFormatter implements Expr.Visitor<String>, Stmt.Visitor<String> {
        String format(Stmt statement) {
            return statement.accept(this);
        }

        private String format(Expr expression) {
            return expression == null ? "nil" : expression.accept(this);
        }

        @Override
        public String visitExpressionStmt(Stmt.ExpressionStmt stmt) {
            return "ExpressionStmt(" + format(stmt.expression) + ")";
        }

        @Override
        public String visitPrintStmt(Stmt.PrintStmt stmt) {
            return "PrintStmt(" + format(stmt.expression) + ")";
        }

        @Override
        public String visitVarStmt(Stmt.VarStmt stmt) {
            return "VarStmt(" + stmt.name.lexeme + ", " + format(stmt.initializer) + ")";
        }

        @Override
        public String visitBlockStmt(Stmt.BlockStmt stmt) {
            return "BlockStmt(" + formatStatements(stmt.statements) + ")";
        }

        @Override
        public String visitIfStmt(Stmt.IfStmt stmt) {
            return "IfStmt(" + format(stmt.condition)
                + ", " + stmt.thenBranch.accept(this)
                + ", " + formatNullableStmt(stmt.elseBranch) + ")";
        }

        @Override
        public String visitForStmt(Stmt.ForStmt stmt) {
            return "ForStmt(" + formatNullableStmt(stmt.initializer)
                + ", " + format(stmt.condition)
                + ", " + format(stmt.increment)
                + ", " + stmt.body.accept(this) + ")";
        }

        @Override
        public String visitWhileStmt(Stmt.WhileStmt stmt) {
            return "WhileStmt(" + format(stmt.condition) + ", " + stmt.body.accept(this) + ")";
        }

        @Override
        public String visitFunctionStmt(Stmt.FunctionStmt stmt) {
            String params = stmt.params.stream()
                .map(token -> token.lexeme)
                .collect(Collectors.joining(", "));
            return "FunctionStmt(" + stmt.name.lexeme + ", [" + params + "], "
                + formatStatements(stmt.body) + ")";
        }

        @Override
        public String visitReturnStmt(Stmt.ReturnStmt stmt) {
            return "ReturnStmt(" + format(stmt.value) + ")";
        }

        @Override
        public String visitLiteral(Expr.Literal expr) {
            return "Literal(" + valueText(expr.value) + ")";
        }

        @Override
        public String visitVariable(Expr.Variable expr) {
            return "Variable(" + expr.name.lexeme + ")";
        }

        @Override
        public String visitAssign(Expr.Assign expr) {
            return "Assign(" + expr.name.lexeme + ", " + format(expr.value) + ")";
        }

        @Override
        public String visitUnary(Expr.Unary expr) {
            return "Unary(" + expr.operator.lexeme + ", " + format(expr.right) + ")";
        }

        @Override
        public String visitBinary(Expr.Binary expr) {
            return "Binary(" + format(expr.left) + ", " + expr.operator.lexeme + ", "
                + format(expr.right) + ")";
        }

        @Override
        public String visitLogical(Expr.Logical expr) {
            return "Logical(" + format(expr.left) + ", " + expr.operator.lexeme + ", "
                + format(expr.right) + ")";
        }

        @Override
        public String visitGrouping(Expr.Grouping expr) {
            return "Grouping(" + format(expr.expression) + ")";
        }

        @Override
        public String visitCall(Expr.Call expr) {
            return "Call(" + format(expr.callee) + ", " + formatExpressions(expr.arguments) + ")";
        }

        @Override
        public String visitArrayGet(Expr.ArrayGet expr) {
            return "ArrayGet(" + format(expr.array) + ", " + format(expr.index) + ")";
        }

        @Override
        public String visitArraySet(Expr.ArraySet expr) {
            return "ArraySet(" + format(expr.array) + ", " + format(expr.index)
                + ", " + format(expr.value) + ")";
        }

        private String formatNullableStmt(Stmt statement) {
            return statement == null ? "nil" : statement.accept(this);
        }

        private String formatStatements(List<Stmt> statements) {
            return statements.stream()
                .map(statement -> statement.accept(this))
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private String formatExpressions(List<Expr> expressions) {
            return expressions.stream()
                .map(this::format)
                .collect(Collectors.joining(", ", "[", "]"));
        }

        private String valueText(Object value) {
            if (value == null) {
                return "nil";
            }
            if (value instanceof String text) {
                return "\"" + escapeString(text) + "\"";
            }
            return Executor.stringify(value);
        }

        private String escapeString(String text) {
            return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        }
    }
}
