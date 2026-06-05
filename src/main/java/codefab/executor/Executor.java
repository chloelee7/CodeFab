package codefab.executor;

import codefab.core.Expr;
import codefab.core.InterpreterRuntimeError;
import codefab.core.OutputSink;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Walks the AST with DFS and produces effects: printing through an injected
 * {@link OutputSink} and mutating an {@link Environment}. Runtime faults are
 * raised as {@link InterpreterRuntimeError}. The Executor never parses or
 * performs static checks.
 */
public final class Executor implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private final OutputSink output;
    private Environment environment;
    // The global scope. Variables the Checker could not resolve to a distance
    // (true globals, natives like Array, and REPL bindings from earlier runs)
    // are looked up here directly.
    private final Environment globals;
    // Static-binding distances computed by the Checker (Variable/Assign -> hops).
    // Absent entries mean "global"; see shared-contracts §9-1. Not final: a REPL
    // session reuses one Executor across runs (to keep globals + natives alive)
    // but the Checker produces a fresh distance map each run, so the session
    // swaps in the new map via setLocals() before executing the folded program.
    private Map<Expr, Integer> locals;
    // Whether a resolver actually supplied a locals map. When false (the legacy
    // (OutputSink, Environment) overload, used by a facade that has not been
    // upgraded to pass CheckResult.locals), unresolved references fall back to
    // a normal chain walk instead of treating them as globals -- this keeps the
    // old end-to-end behaviour intact for callers that never resolve distances.
    private final boolean resolved;
    // The closing paren of the call currently in flight, so native callables can
    // attach a meaningful line number to runtime faults (e.g. Array(...)).
    private Token currentCallToken;

    /** Backwards-compatible overload: no resolved locals. Variable/Assign use a
     * normal scope-chain walk, exactly as before static binding existed. */
    public Executor(OutputSink output, Environment globals) {
        this.output = output;
        this.environment = globals;
        this.globals = globals;
        this.locals = Collections.emptyMap();
        this.resolved = false;
        defineNatives(globals);
    }

    public Executor(OutputSink output, Environment globals, Map<Expr, Integer> locals) {
        this.output = output;
        this.environment = globals;
        this.globals = globals;
        this.locals = locals;
        this.resolved = true;
        defineNatives(globals);
    }

    /**
     * Swap in a fresh static-binding distance map for the next program run.
     * A persistent REPL session reuses one Executor (keeping its globals and
     * native bindings) but re-checks each input, yielding a new locals map whose
     * keys are the freshly folded AST nodes. References this map does not record
     * fall back to the global scope ({@code resolved} stays true), so variables
     * defined in earlier runs are still found via {@code globals}. Only valid on
     * an Executor built with the resolved (3-arg) constructor.
     */
    public void setLocals(Map<Expr, Integer> locals) {
        this.locals = locals;
    }

    /** Bind native callables (e.g. {@code Array}) into the global scope so a
     * source-level call resolves them as ordinary variables. */
    private void defineNatives(Environment globals) {
        globals.define("Array", new CodeFabCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Executor executor, List<Object> arguments) {
                Object sizeArg = arguments.get(0);
                if (!(sizeArg instanceof Double)) {
                    throw new InterpreterRuntimeError(currentCallToken,
                            "Array size must be a number.");
                }
                double d = (double) sizeArg;
                if (d != Math.floor(d) || Double.isInfinite(d) || d < 0) {
                    // non-integral or negative sizes are not valid array sizes
                    throw new InterpreterRuntimeError(currentCallToken,
                            "Array size must be a number.");
                }
                return new CodeFabArray((int) d);
            }

            @Override
            public String toString() {
                return "<native fn Array>";
            }
        });
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

    @Override
    public Void visitWhileStmt(Stmt.WhileStmt stmt) {
        // No dedicated scope: while declares no loop variable. If the body is a
        // BlockStmt, visitBlockStmt opens and restores its own environment.
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    /**
     * Run statements in a fresh environment, always restoring the previous one.
     * Package-visible so {@link CodeFabFunction} can run a body in its call frame.
     */
    void executeBlock(List<Stmt> statements, Environment blockEnv) {
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

    @Override
    public Void visitFunctionStmt(Stmt.FunctionStmt stmt) {
        // The current environment is the closure: a recursive call sees its own
        // name because the binding is added to that same environment.
        CodeFabFunction function = new CodeFabFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.ReturnStmt stmt) {
        Object value = null; // `return;` yields nil
        if (stmt.value != null) {
            value = evaluate(stmt.value);
        }
        throw new Return(value);
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
        Integer distance = locals.get(expr);
        if (distance != null) {
            // Resolved: jump straight to the declaring scope (O(1), no search).
            return environment.getAt(distance, expr.name.lexeme);
        }
        if (resolved) {
            // Resolver ran but did not record this access -> it is a global
            // (true global / native / earlier-run REPL binding).
            return globals.get(expr.name);
        }
        // No resolver: fall back to the legacy chain walk.
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssign(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else if (resolved) {
            globals.assign(expr.name, value);
        } else {
            // No resolver: fall back to the legacy chain walk.
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
            case PERCENT:
                checkNumberOperands(op, left, right);
                if ((double) right == 0.0) {
                    // remainder by zero is still a division by zero
                    throw new InterpreterRuntimeError(op, "Division by zero.");
                }
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
    public Object visitCall(Expr.Call expr) {
        Object callee = evaluate(expr.callee);
        if (!(callee instanceof CodeFabCallable)) {
            throw new InterpreterRuntimeError(expr.paren, "Can only call functions.");
        }
        CodeFabCallable callable = (CodeFabCallable) callee;
        java.util.List<Object> arguments = new java.util.ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument)); // left-to-right
        }
        if (arguments.size() != callable.arity()) {
            throw new InterpreterRuntimeError(expr.paren,
                    "Expected " + callable.arity() + " arguments but got "
                            + arguments.size() + ".");
        }
        // Expose this call's paren so native callables can report a line number.
        Token previousCallToken = currentCallToken;
        currentCallToken = expr.paren;
        try {
            return callable.call(this, arguments);
        } finally {
            currentCallToken = previousCallToken;
        }
    }

    @Override
    public Object visitIndex(Expr.Index expr) {
        CodeFabArray array = asArray(evaluate(expr.target), expr.bracket);
        int index = asIndex(array, evaluate(expr.index), expr.bracket);
        return array.get(index);
    }

    @Override
    public Object visitIndexSet(Expr.IndexSet expr) {
        CodeFabArray array = asArray(evaluate(expr.target), expr.bracket);
        int index = asIndex(array, evaluate(expr.index), expr.bracket);
        Object value = evaluate(expr.value);
        array.set(index, value);
        return value; // the value of an assignment is the assigned value
    }

    private CodeFabArray asArray(Object target, Token bracket) {
        if (target instanceof CodeFabArray) return (CodeFabArray) target;
        throw new InterpreterRuntimeError(bracket, "Can only index arrays.");
    }

    private int asIndex(CodeFabArray array, Object index, Token bracket) {
        if (!(index instanceof Double)) {
            throw new InterpreterRuntimeError(bracket, "Array index must be a number.");
        }
        double d = (double) index;
        if (d != Math.floor(d) || Double.isInfinite(d) || d < 0 || d >= array.size()) {
            throw new InterpreterRuntimeError(bracket, "Array index out of bounds.");
        }
        return (int) d;
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
