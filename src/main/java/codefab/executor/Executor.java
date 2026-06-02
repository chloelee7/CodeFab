package codefab.executor;

import codefab.core.Expr;
import codefab.core.InterpreterRuntimeError;
import codefab.core.OutputSink;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;

import java.util.List;

/**
 * Walks the AST with DFS and produces effects: printing through an injected
 * {@link OutputSink} and mutating an {@link Environment}. Runtime faults are
 * raised as {@link InterpreterRuntimeError}. The Executor never parses or
 * performs static checks.
 */
public final class Executor implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private final OutputSink output;
    private Environment environment;

    public Executor(OutputSink output, Environment globals) {
        this.output = output;
        this.environment = globals;
    }

    public void execute(List<Stmt> statements) {
        for (Stmt statement : statements) {
            execute(statement);
        }
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    // --- statements --------------------------------------------------------

    @Override
    public Void visitExpressionStmt(Stmt.ExpressionStmt stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.PrintStmt stmt) {
        Object value = evaluate(stmt.expression);
        output.print(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.VarStmt stmt) {
        Object value = null; // uninitialized variables hold nil
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.BlockStmt stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.IfStmt stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.ForStmt stmt) {
        // Give the loop its own scope so the initializer variable does not leak.
        Environment previous = this.environment;
        try {
            this.environment = new Environment(previous);
            if (stmt.initializer != null) execute(stmt.initializer);
            while (stmt.condition == null || isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
                if (stmt.increment != null) evaluate(stmt.increment);
            }
        } finally {
            this.environment = previous;
        }
        return null;
    }

    /** Run statements in a fresh environment, always restoring the previous one. */
    private void executeBlock(List<Stmt> statements, Environment blockEnv) {
        Environment previous = this.environment;
        try {
            this.environment = blockEnv;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    // --- expressions -------------------------------------------------------

    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        return evaluate(expr.expression);
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
    public Object visitLogical(Expr.Logical expr) {
        Object left = evaluate(expr.left);
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else { // AND
            if (!isTruthy(left)) return left;
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitBinary(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
        Token op = expr.operator;
        switch (op.type) {
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }
                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                throw new InterpreterRuntimeError(op,
                        "Operands must be two numbers or two strings.");
            case MINUS:
                checkNumberOperands(op, left, right);
                return (double) left - (double) right;
            case STAR:
                checkNumberOperands(op, left, right);
                return (double) left * (double) right;
            case SLASH:
                checkNumberOperands(op, left, right);
                if ((double) right == 0.0) {
                    throw new InterpreterRuntimeError(op, "Division by zero.");
                }
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

    // --- semantics helpers -------------------------------------------------

    private boolean isTruthy(Object value) {
        if (value == null) return false;          // nil is falsey
        if (value instanceof Boolean) return (Boolean) value;
        return true;                              // every other value is truthy
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

    /** Render a runtime value the way {@code print} should show it. */
    public static String stringify(Object value) {
        if (value == null) return "nil";
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d) && !Double.isNaN(d)) {
                return Long.toString((long) d); // drop the ".0" for integral values
            }
            return Double.toString(d);
        }
        return value.toString();
    }
}
