package codefab.executor;

import codefab.core.Expr;
import codefab.core.InterpreterRuntimeError;
import codefab.core.OutputSink;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;

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
        Object value = stmt.initializer != null ? evaluate(stmt.initializer) : null;
        environment.define(stmt.name.lexeme, value);
        return null;
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
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssign(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitUnary(Expr.Unary expr) {
        Object right = evaluate(expr.right);
        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
            case PLUS:
                checkNumberOperand(expr.operator, right);
                return (double) right;
            case BANG:
                return !isTruthy(right);
            default:
                throw new InterpreterRuntimeError(expr.operator, "Unknown unary operator.");
        }
    }

    @Override
    public Object visitBinary(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
        Token op = expr.operator;
        switch (op.type) {
            case PLUS:
                if (left instanceof Double && right instanceof Double)
                    return (double) left + (double) right;
                if (left instanceof String && right instanceof String)
                    return (String) left + (String) right;
                throw new InterpreterRuntimeError(op, "Operands must be two numbers or two strings.");
            case MINUS:
                checkNumberOperands(op, left, right);
                return (double) left - (double) right;
            case STAR:
                checkNumberOperands(op, left, right);
                return (double) left * (double) right;
            case SLASH:
                checkNumberOperands(op, left, right);
                if ((double) right == 0.0)
                    throw new InterpreterRuntimeError(op, "Division by zero.");
                return (double) left / (double) right;
            case GREATER:
                checkNumberOperands(op, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(op, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(op, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(op, left, right);
                return (double) left <= (double) right;
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case BANG_EQUAL:
                return !isEqual(left, right);
            default:
                throw new InterpreterRuntimeError(op, "Unknown binary operator.");
        }
    }

    @Override
    public Object visitLogical(Expr.Logical expr) {
        throw new UnsupportedOperationException("visitLogical not implemented");
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    // --- helpers -----------------------------------------------------------

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new InterpreterRuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new InterpreterRuntimeError(operator, "Operands must be numbers.");
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
