package codefab.executor;

import codefab.core.Expr;
import codefab.core.InterpreterRuntimeError;
import codefab.core.OutputSink;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Executor implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private final OutputSink output;
    private Environment environment;

    // Array() 내장 함수 식별용 sentinel
    private static final Object ARRAY_BUILTIN = new Object();
    private static final Object LEN_BUILTIN = new Object();

    private static final int MAX_CALL_DEPTH = 500;
    private int callDepth = 0;

    public Executor(OutputSink output, Environment globals) {
        this.output = output;
        this.environment = globals;
        globals.define("len", LEN_BUILTIN);
    }

    public void execute(List<Stmt> statements) {
        for (Stmt statement : statements) {
            statement.accept(this);
        }
    }

    public Environment getEnvironment() {
        return environment;
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
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    public void executeBlock(List<Stmt> statements, Environment env) {
        Environment previous = this.environment;
        try {
            this.environment = env;
            for (Stmt s : statements) {
                s.accept(this);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitIfStmt(Stmt.IfStmt stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            stmt.thenBranch.accept(this);
        } else if (stmt.elseBranch != null) {
            stmt.elseBranch.accept(this);
        }
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.ForStmt stmt) {
        withNewScope(() -> {
            if (stmt.initializer != null) stmt.initializer.accept(this);
            while (stmt.condition == null || isTruthy(evaluate(stmt.condition))) {
                stmt.body.accept(this);
                if (stmt.increment != null) evaluate(stmt.increment);
            }
        });
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.WhileStmt stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            stmt.body.accept(this);
        }
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.FunctionStmt stmt) {
        CodeFabFunction function = new CodeFabFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.ReturnStmt stmt) {
        Object value = stmt.value != null ? evaluate(stmt.value) : null;
        throw new ReturnException(value);
    }

    // --- expressions -------------------------------------------------------

    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitVariable(Expr.Variable expr) {
        // ARRAY 토큰은 환경 조회 없이 내장 함수 sentinel 반환
        if (expr.name.type == TokenType.ARRAY) return ARRAY_BUILTIN;
        if (expr.distance >= 0) {
            return environment.getAt(expr.distance, expr.name.lexeme);
        }
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssign(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        if (expr.distance >= 0) {
            environment.assignAt(expr.distance, expr.name.lexeme, value);
        } else {
            environment.assign(expr.name, value);
        }
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
            case PERCENT:
                checkNumberOperands(op, left, right);
                if ((double) right == 0.0)
                    throw new InterpreterRuntimeError(op, "Division by zero.");
                return (double) left % (double) right;
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
        Object left = evaluate(expr.left);
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitCall(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        // Array() 내장 함수 처리
        if (callee == ARRAY_BUILTIN) {
            if (expr.arguments.size() != 1) {
                throw new InterpreterRuntimeError(expr.paren,
                        "Array() takes exactly 1 argument.");
            }
            Object sizeObj = evaluate(expr.arguments.get(0));
            if (!(sizeObj instanceof Double)) {
                throw new InterpreterRuntimeError(expr.paren,
                        "Array size must be a number.");
            }
            double sizeDouble = (Double) sizeObj;
            if (sizeDouble != Math.floor(sizeDouble) || Double.isInfinite(sizeDouble)) {
                throw new InterpreterRuntimeError(expr.paren,
                        "Array size must be an integer.");
            }
            int size = (int) sizeDouble;
            if (size < 0) {
                throw new InterpreterRuntimeError(expr.paren,
                        "Array size must be non-negative.");
            }
            return new ArrayList<>(Collections.nCopies(size, null));
        }

        if (callee == LEN_BUILTIN) {
            if (expr.arguments.size() != 1) {
                throw new InterpreterRuntimeError(expr.paren,
                        "len() takes exactly 1 argument.");
            }
            Object value = evaluate(expr.arguments.get(0));
            if (!(value instanceof List)) {
                throw new InterpreterRuntimeError(expr.paren,
                        "len() expects an array.");
            }
            return (double) ((List<?>) value).size();
        }

        if (!(callee instanceof CodeFabFunction)) {
            throw new InterpreterRuntimeError(expr.paren,
                    "Can only call functions.");
        }

        CodeFabFunction function = (CodeFabFunction) callee;

        if (expr.arguments.size() != function.arity()) {
            throw new InterpreterRuntimeError(expr.paren,
                    "Expected " + function.arity() + " arguments but got " + expr.arguments.size() + ".");
        }

        List<Object> args = new ArrayList<>();
        for (Expr arg : expr.arguments) {
            args.add(evaluate(arg));
        }

        if (callDepth >= MAX_CALL_DEPTH) {
            throw new InterpreterRuntimeError(expr.paren,
                    "Maximum call depth (" + MAX_CALL_DEPTH + ") exceeded.");
        }

        Environment callEnv = new Environment(function.closure);
        for (int i = 0; i < function.declaration.params.size(); i++) {
            callEnv.define(function.declaration.params.get(i).lexeme, args.get(i));
        }

        callDepth++;
        try {
            executeBlock(function.declaration.body, callEnv);
            return null;
        } catch (ReturnException ret) {
            return ret.value;
        } finally {
            callDepth--;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitArrayGet(Expr.ArrayGet expr) {
        Object arr = evaluate(expr.array);
        Object idx = evaluate(expr.index);

        if (!(arr instanceof List)) {
            throw new InterpreterRuntimeError(expr.bracket,
                    "Only arrays can be indexed.");
        }
        if (!(idx instanceof Double)) {
            throw new InterpreterRuntimeError(expr.bracket,
                    "Array index must be a number.");
        }

        List<Object> list = (List<Object>) arr;
        double idxDouble = (Double) idx;
        if (idxDouble != Math.floor(idxDouble) || Double.isInfinite(idxDouble)) {
            throw new InterpreterRuntimeError(expr.bracket,
                    "Array index must be an integer.");
        }
        int i = (int) idxDouble;
        if (i < 0 || i >= list.size()) {
            throw new InterpreterRuntimeError(expr.bracket,
                    "Array index " + i + " out of bounds (size " + list.size() + ").");
        }
        return list.get(i);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitArraySet(Expr.ArraySet expr) {
        Object arr = evaluate(expr.array);
        Object idx = evaluate(expr.index);
        Object val = evaluate(expr.value);

        if (!(arr instanceof List)) {
            throw new InterpreterRuntimeError(expr.bracket,
                    "Only arrays can be indexed.");
        }
        if (!(idx instanceof Double)) {
            throw new InterpreterRuntimeError(expr.bracket,
                    "Array index must be a number.");
        }

        List<Object> list = (List<Object>) arr;
        double idxDouble = (Double) idx;
        if (idxDouble != Math.floor(idxDouble) || Double.isInfinite(idxDouble)) {
            throw new InterpreterRuntimeError(expr.bracket,
                    "Array index must be an integer.");
        }
        int i = (int) idxDouble;
        if (i < 0 || i >= list.size()) {
            throw new InterpreterRuntimeError(expr.bracket,
                    "Array index " + i + " out of bounds (size " + list.size() + ").");
        }
        list.set(i, val);
        return val;
    }

    // --- helpers -----------------------------------------------------------

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void withNewScope(Runnable body) {
        Environment previous = this.environment;
        try {
            this.environment = new Environment(previous);
            body.run();
        } finally {
            this.environment = previous;
        }
    }

    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        // 배열은 참조(identity) 기준 비교 — Java 구조적 동등성 누출 방지
        if (a instanceof List || b instanceof List) return a == b;
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

    @SuppressWarnings("unchecked")
    public static String stringify(Object value) {
        if (value == null) return "nil";
        if (value == ARRAY_BUILTIN) return "<Array builtin>";
        if (value == LEN_BUILTIN) return "<native fn len>";
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d) && !Double.isNaN(d)) {
                return Long.toString((long) d);
            }
            return Double.toString(d);
        }
        if (value instanceof List) {
            // 배열 요소를 CodeFab 값 포맷으로 출력 (null → nil)
            List<Object> list = (List<Object>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(stringify(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }
}
