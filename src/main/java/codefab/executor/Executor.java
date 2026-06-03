package codefab.executor;

import codefab.core.Expr;
import codefab.core.OutputSink;
import codefab.core.Stmt;

import java.util.List;

public final class Executor implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private final OutputSink output;
    private Environment environment;

    public Executor(OutputSink output, Environment globals) {
        this.output = output;
        this.environment = globals;
    }

    public void execute(List<Stmt> statements) {
        for (Stmt statement : statements) {
            statement.accept(this);
        }
    }

    // --- statements --------------------------------------------------------

    @Override
    public Void visitExpressionStmt(Stmt.ExpressionStmt stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.PrintStmt stmt) {
        output.print(stringify(evaluate(stmt.expression)));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.VarStmt stmt) {
        throw new UnsupportedOperationException("visitVarStmt not implemented");
    }

    @Override
    public Void visitBlockStmt(Stmt.BlockStmt stmt) {
        throw new UnsupportedOperationException("visitBlockStmt not implemented");
    }

    @Override
    public Void visitIfStmt(Stmt.IfStmt stmt) {
        throw new UnsupportedOperationException("visitIfStmt not implemented");
    }

    @Override
    public Void visitForStmt(Stmt.ForStmt stmt) {
        throw new UnsupportedOperationException("visitForStmt not implemented");
    }

    // --- expressions -------------------------------------------------------

    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitVariable(Expr.Variable expr) {
        throw new UnsupportedOperationException("visitVariable not implemented");
    }

    @Override
    public Object visitAssign(Expr.Assign expr) {
        throw new UnsupportedOperationException("visitAssign not implemented");
    }

    @Override
    public Object visitUnary(Expr.Unary expr) {
        throw new UnsupportedOperationException("visitUnary not implemented");
    }

    @Override
    public Object visitBinary(Expr.Binary expr) {
        throw new UnsupportedOperationException("visitBinary not implemented");
    }

    @Override
    public Object visitLogical(Expr.Logical expr) {
        throw new UnsupportedOperationException("visitLogical not implemented");
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        throw new UnsupportedOperationException("visitGrouping not implemented");
    }

    // --- helpers -----------------------------------------------------------

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    public static String stringify(Object value) {
        if (value == null) return "nil";
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d) && !Double.isNaN(d)) {
                return Long.toString((long) d);
            }
            return Double.toString(d);
        }
        return value.toString();
    }
}
