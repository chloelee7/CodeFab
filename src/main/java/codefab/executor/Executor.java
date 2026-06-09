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

    public static final int DEFAULT_MAX_CALL_DEPTH = 500;
    private final int maxCallDepth;
    private int callDepth = 0;

    /**
     * globals는 반드시 {@link #newGlobalScope()}로 생성해야 한다. 그렇지 않으면
     * 네이티브 함수(Array, len, chr 등)가 누락된 채 조용히 실행된다.
     * 생성자는 globals의 enclosing이 builtin 스코프인지 검증하여 잘못된 주입을 막는다 (계약 §8-0).
     */
    public Executor(OutputSink output, Environment globals) {
        this(output, globals, DEFAULT_MAX_CALL_DEPTH);
    }

    /**
     * globals는 반드시 {@link #newGlobalScope()}로 생성해야 한다. 그렇지 않으면
     * 네이티브 함수(Array, len, chr 등)가 누락된 채 조용히 실행된다.
     * 생성자는 globals의 enclosing이 builtin 스코프인지 검증하여 잘못된 주입을 막는다 (계약 §8-0).
     */
    public Executor(OutputSink output, Environment globals, int maxCallDepth) {
        // newGlobalScope()는 builtin ← globals 체인을 만든다. globals의 enclosing이
        // builtin 스코프가 아니면 네이티브 함수가 누락된 잘못된 globals이므로 즉시 거부한다.
        Environment enclosing = globals.enclosing;
        if (enclosing == null || !enclosing.isBuiltinScope()) {
            throw new IllegalArgumentException(
                    "globals must be created via Executor.newGlobalScope()");
        }
        this.output = output;
        this.environment = globals;
        this.maxCallDepth = maxCallDepth;
        // 네이티브 함수는 newGlobalScope() 팩토리 경로에서만 builtin 스코프에 등록된다 (계약 §8-0).
    }

    /**
     * 네이티브 함수를 담은 builtin 스코프를 만들고, 그 위에 빈 globals 스코프를 얹어 반환한다.
     * 체인: builtin ← globals. 모든 진입점은 이 팩토리로 globals를 생성한다 (계약 §8-0).
     */
    public static Environment newGlobalScope() {
        Environment builtin = Environment.newBuiltinScope();
        defineNativeFunctions(builtin);
        return new Environment(builtin);
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
        evaluate(stmt.expression());
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.PrintStmt stmt) {
        output.print(stringify(evaluate(stmt.expression())));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.VarStmt stmt) {
        Object value = stmt.initializer() != null ? evaluate(stmt.initializer()) : null;
        environment.define(stmt.name().lexeme, value);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.BlockStmt stmt) {
        executeBlock(stmt.statements(), new Environment(environment));
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
        if (isTruthy(evaluate(stmt.condition()))) {
            stmt.thenBranch().accept(this);
        } else if (stmt.elseBranch() != null) {
            stmt.elseBranch().accept(this);
        }
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.ForStmt stmt) {
        withNewScope(() -> {
            if (stmt.initializer() != null) stmt.initializer().accept(this);
            while (stmt.condition() == null || isTruthy(evaluate(stmt.condition()))) {
                stmt.body().accept(this);
                if (stmt.increment() != null) evaluate(stmt.increment());
            }
        });
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.WhileStmt stmt) {
        while (isTruthy(evaluate(stmt.condition()))) {
            stmt.body().accept(this);
        }
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.FunctionStmt stmt) {
        CodeFabFunction function = new CodeFabFunction(stmt, environment);
        environment.define(stmt.name().lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.ReturnStmt stmt) {
        Object value = stmt.value() != null ? evaluate(stmt.value()) : null;
        throw new ReturnException(value);
    }

    // --- expressions -------------------------------------------------------

    @Override
    public Object visitLiteral(Expr.Literal expr) {
        return expr.value();
    }

    @Override
    public Object visitVariable(Expr.Variable expr) {
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
        Object right = evaluate(expr.right());
        switch (expr.operator().type) {
            case MINUS:
                checkNumberOperand(expr.operator(), right);
                return -(double) right;
            case PLUS:
                checkNumberOperand(expr.operator(), right);
                return (double) right;
            case BANG:
                return !isTruthy(right);
            default:
                throw new InterpreterRuntimeError(expr.operator(), "Unknown unary operator.");
        }
    }

    @Override
    public Object visitBinary(Expr.Binary expr) {
        Object left = evaluate(expr.left());
        Object right = evaluate(expr.right());
        Token op = expr.operator();
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
        Object left = evaluate(expr.left());
        if (expr.operator().type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }
        return evaluate(expr.right());
    }

    @Override
    public Object visitGrouping(Expr.Grouping expr) {
        return evaluate(expr.expression());
    }

    @Override
    public Object visitCall(Expr.Call expr) {
        Object callee = evaluate(expr.callee());

        if (!(callee instanceof CodeFabCallable)) {
            throw new InterpreterRuntimeError(expr.paren(),
                    "Can only call functions.");
        }

        CodeFabCallable callable = (CodeFabCallable) callee;

        if (expr.arguments().size() != callable.arity()) {
            throw new InterpreterRuntimeError(expr.paren(),
                    "Expected " + callable.arity() + " arguments but got " + expr.arguments().size() + ".");
        }

        List<Object> args = new ArrayList<>();
        for (Expr arg : expr.arguments()) {
            args.add(evaluate(arg));
        }

        return callable.call(this, expr.paren(), args);
    }

    Object callUserFunction(CodeFabFunction function, Token token, List<Object> args) {
        if (callDepth >= maxCallDepth) {
            throw new InterpreterRuntimeError(token,
                    "Maximum call depth (" + maxCallDepth + ") exceeded.");
        }

        Environment callEnv = new Environment(function.closure);
        for (int i = 0; i < function.declaration.params().size(); i++) {
            callEnv.define(function.declaration.params().get(i).lexeme, args.get(i));
        }

        callDepth++;
        try {
            executeBlock(function.declaration.body(), callEnv);
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
        Object arr = evaluate(expr.array());
        Object idx = evaluate(expr.index());

        if (!(arr instanceof List)) {
            throw new InterpreterRuntimeError(expr.bracket(),
                    "Only arrays can be indexed.");
        }
        if (!(idx instanceof Double)) {
            throw new InterpreterRuntimeError(expr.bracket(),
                    "Array index must be a number.");
        }

        List<Object> list = (List<Object>) arr;
        double idxDouble = (Double) idx;
        if (idxDouble != Math.floor(idxDouble) || Double.isInfinite(idxDouble)) {
            throw new InterpreterRuntimeError(expr.bracket(),
                    "Array index must be an integer.");
        }
        int i = (int) idxDouble;
        if (i < 0 || i >= list.size()) {
            throw new InterpreterRuntimeError(expr.bracket(),
                    "Array index " + i + " out of bounds (size " + list.size() + ").");
        }
        return list.get(i);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object visitArraySet(Expr.ArraySet expr) {
        Object arr = evaluate(expr.array());
        Object idx = evaluate(expr.index());
        Object val = evaluate(expr.value());

        if (!(arr instanceof List)) {
            throw new InterpreterRuntimeError(expr.bracket(),
                    "Only arrays can be indexed.");
        }
        if (!(idx instanceof Double)) {
            throw new InterpreterRuntimeError(expr.bracket(),
                    "Array index must be a number.");
        }

        List<Object> list = (List<Object>) arr;
        double idxDouble = (Double) idx;
        if (idxDouble != Math.floor(idxDouble) || Double.isInfinite(idxDouble)) {
            throw new InterpreterRuntimeError(expr.bracket(),
                    "Array index must be an integer.");
        }
        int i = (int) idxDouble;
        if (i < 0 || i >= list.size()) {
            throw new InterpreterRuntimeError(expr.bracket(),
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

    private static void defineNativeFunctions(Environment builtin) {
        builtin.define("Array", new NativeFunction("Array", 1) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                Object sizeObj = arguments.get(0);
                if (!(sizeObj instanceof Double)) {
                    throw new InterpreterRuntimeError(token, "Array size must be a number.");
                }
                int size = requireInteger(token, sizeObj, "Array size must be an integer.");
                if (size < 0) {
                    throw new InterpreterRuntimeError(token, "Array size must be non-negative.");
                }
                return new ArrayList<>(Collections.nCopies(size, null));
            }
        });

        builtin.define("len", new NativeFunction("len", 1) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                Object value = arguments.get(0);
                if (value instanceof String) {
                    return (double) ((String) value).length();
                }
                if (value instanceof List) {
                    return (double) ((List<?>) value).size();
                }
                throw new InterpreterRuntimeError(token, "len() expects a string or array.");
            }
        });

        builtin.define("charAt", new NativeFunction("charAt", 2) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                Object sourceObj = arguments.get(0);
                if (!(sourceObj instanceof String)) {
                    throw new InterpreterRuntimeError(token, "charAt() expects a string.");
                }
                String source = (String) sourceObj;
                int index = requireInteger(token, arguments.get(1), "String index must be an integer.");
                if (index < 0 || index >= source.length()) {
                    throw new InterpreterRuntimeError(token, "String index out of bounds.");
                }
                return Character.toString(source.charAt(index));
            }
        });

        builtin.define("slice", new NativeFunction("slice", 3) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                Object sourceObj = arguments.get(0);
                if (!(sourceObj instanceof String)) {
                    throw new InterpreterRuntimeError(token, "slice() expects a string.");
                }
                String source = (String) sourceObj;
                int start = requireInteger(token, arguments.get(1), "String index must be an integer.");
                int end = requireInteger(token, arguments.get(2), "String index must be an integer.");
                if (start < 0 || end < start || end > source.length()) {
                    throw new InterpreterRuntimeError(token, "String slice out of bounds.");
                }
                return source.substring(start, end);
            }
        });

        builtin.define("push", new NativeFunction("push", 2) {
            @Override
            @SuppressWarnings("unchecked")
            public Object call(Executor executor, Token token, List<Object> arguments) {
                Object target = arguments.get(0);
                if (!(target instanceof List)) {
                    throw new InterpreterRuntimeError(token, "push() expects an array.");
                }
                List<Object> list = (List<Object>) target;
                list.add(arguments.get(1));
                return (double) list.size();
            }
        });

        builtin.define("chr", new NativeFunction("chr", 1) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                int code = requireInteger(token, arguments.get(0),
                        "chr() expects an integer character code.");
                if (code < 0 || code > Character.MAX_VALUE) {
                    throw new InterpreterRuntimeError(token,
                            "chr() expects an integer character code.");
                }
                return Character.toString((char) code);
            }
        });

        builtin.define("num", new NativeFunction("num", 1) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                Object value = arguments.get(0);
                if (!(value instanceof String)) {
                    throw new InterpreterRuntimeError(token, "num() expects a numeric string.");
                }
                try {
                    return Double.parseDouble((String) value);
                } catch (NumberFormatException error) {
                    throw new InterpreterRuntimeError(token, "num() expects a numeric string.");
                }
            }
        });

        builtin.define("ord", new NativeFunction("ord", 1) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                Object value = arguments.get(0);
                if (!(value instanceof String) || ((String) value).length() != 1) {
                    throw new InterpreterRuntimeError(token, "ord() expects a one-character string.");
                }
                return (double) ((String) value).charAt(0);
            }
        });

        builtin.define("typeOf", new NativeFunction("typeOf", 1) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                Object value = arguments.get(0);
                if (value == null) {
                    return "nil";
                }
                if (value instanceof Boolean) {
                    return "Boolean";
                }
                if (value instanceof Double) {
                    return "Number";
                }
                if (value instanceof String) {
                    return "String";
                }
                if (value instanceof List) {
                    return "Array";
                }
                if (value instanceof CodeFabCallable) {
                    return "Function";
                }
                return value.getClass().getSimpleName();
            }
        });

        builtin.define("valueText", new NativeFunction("valueText", 1) {
            @Override
            public Object call(Executor executor, Token token, List<Object> arguments) {
                return stringify(arguments.get(0));
            }
        });
    }

    private static int requireInteger(Token token, Object value, String message) {
        if (!(value instanceof Double)) {
            throw new InterpreterRuntimeError(token, message);
        }
        double number = (Double) value;
        if (number != Math.floor(number) || Double.isInfinite(number)) {
            throw new InterpreterRuntimeError(token, message);
        }
        return (int) number;
    }

    @SuppressWarnings("unchecked")
    public static String stringify(Object value) {
        if (value == null) return "nil";
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
