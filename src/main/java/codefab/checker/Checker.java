package codefab.checker;

import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Static semantic analysis, variable resolution, and constant folding over the
 * AST, performed by DFS before execution.
 *
 * <p>The Checker now runs two pre-execution optimizations on top of error
 * checking (contract §9). It first constant-folds the program into a new AST
 * (collapsing fully-constant sub-expressions into single literals), then walks
 * that folded tree to (a) detect the four classes of static error and (b) record
 * a static-binding distance for every {@code Variable}/{@code Assign} reference.
 * Both products are returned as a {@link CheckResult}; diagnostics still
 * accumulate into the injected list. It never executes code.
 *
 * <p>The four static errors: declaring two variables with the same name in one
 * scope, reading a variable inside its own initializer, returning from top-level
 * code, and duplicate parameter names. Non-function call targets and
 * argument-count mismatches are runtime concerns and are not checked here.
 */
public final class Checker implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    /** A name is DECLARED once its statement is seen, DEFINED once its initializer is checked. */
    private enum VarState { DECLARED, DEFINED }

    /** Tracks whether resolution is currently inside a function body, to flag stray returns. */
    private enum FunctionType { NONE, FUNCTION }

    private final List<Diagnostic> diagnostics;
    private final Deque<Map<String, VarState>> scopes = new ArrayDeque<>();
    // Static-binding distances, keyed by node identity (folded-tree nodes).
    private final Map<Expr, Integer> locals = new IdentityHashMap<>();
    private FunctionType currentFunction = FunctionType.NONE;

    public Checker(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Check a whole program: constant-fold it, then resolve the folded tree for
     * static errors and variable distances. A top-level scope models global
     * declarations. Returns the folded program plus the locals distance map; the
     * locals keys are nodes of the folded program so they match what the Executor
     * walks.
     */
    public CheckResult check(List<Stmt> statements) {
        List<Stmt> folded = new ConstantFolder().fold(statements);
        beginScope();
        resolve(folded);
        endScope();
        return new CheckResult(folded, locals);
    }

    // --- statements --------------------------------------------------------

    @Override
    public Void visitVarStmt(Stmt.VarStmt stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.BlockStmt stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.IfStmt stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.ForStmt stmt) {
        // The loop owns a scope so its initializer variable does not leak.
        beginScope();
        if (stmt.initializer != null) resolve(stmt.initializer);
        if (stmt.condition != null) resolve(stmt.condition);
        if (stmt.increment != null) resolve(stmt.increment);
        resolve(stmt.body);
        endScope();
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.WhileStmt stmt) {
        // While declares no loop variable, so it opens no scope of its own;
        // a BlockStmt body opens its own scope when resolved.
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.FunctionStmt stmt) {
        // Declare and define the name before resolving the body so recursion can
        // reference the function from within itself.
        declare(stmt.name);
        define(stmt.name);

        FunctionType enclosingFunction = currentFunction;
        currentFunction = FunctionType.FUNCTION;
        beginScope();
        for (Token param : stmt.params) {
            // Duplicate parameter names are caught by declare's existing check.
            declare(param);
            define(param);
        }
        resolve(stmt.body);
        endScope();
        currentFunction = enclosingFunction;
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.ReturnStmt stmt) {
        if (currentFunction == FunctionType.NONE) {
            report(stmt.keyword, "Can't return from top-level code.");
        }
        if (stmt.value != null) {
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.PrintStmt stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.ExpressionStmt stmt) {
        resolve(stmt.expression);
        return null;
    }

    // --- expressions -------------------------------------------------------

    @Override
    public Void visitVariable(Expr.Variable expr) {
        Map<String, VarState> scope = scopes.peek();
        if (scope != null && scope.get(expr.name.lexeme) == VarState.DECLARED) {
            report(expr.name, "Can't read local variable in initializer.");
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitAssign(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinary(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitLogical(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnary(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCall(Expr.Call expr) {
        // Argument count is a runtime concern; here we only resolve sub-expressions
        // (which also records distances for any Variable/Assign nodes within).
        resolve(expr.callee);
        for (Expr argument : expr.arguments) {
            resolve(argument);
        }
        return null;
    }

    @Override
    public Void visitIndex(Expr.Index expr) {
        // Array bounds/type faults are runtime concerns; only resolve sub-expressions.
        resolve(expr.target);
        resolve(expr.index);
        return null;
    }

    @Override
    public Void visitIndexSet(Expr.IndexSet expr) {
        resolve(expr.target);
        resolve(expr.index);
        resolve(expr.value);
        return null;
    }

    @Override
    public Void visitGrouping(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteral(Expr.Literal expr) {
        return null;
    }

    // --- scope management --------------------------------------------------

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        scopes.pop();
    }

    private void declare(Token name) {
        Map<String, VarState> scope = scopes.peek();
        if (scope == null) return;
        if (scope.containsKey(name.lexeme)) {
            report(name, "Already a variable with this name in this scope.");
        }
        scope.put(name.lexeme, VarState.DECLARED);
    }

    private void define(Token name) {
        Map<String, VarState> scope = scopes.peek();
        if (scope == null) return;
        scope.put(name.lexeme, VarState.DEFINED);
    }

    /**
     * Record the static-binding distance for a {@code Variable}/{@code Assign}
     * node: scan the scope stack from innermost (peek) outward and store the
     * index of the first frame that holds the name (0 = current scope). If no
     * frame holds it, leave it out of the map so the Executor falls back to the
     * global environment (e.g. native {@code Array} or a variable from an earlier
     * REPL run that this fresh Checker's empty global frame does not know).
     */
    private void resolveLocal(Expr expr, Token name) {
        int distance = 0;
        for (Iterator<Map<String, VarState>> it = scopes.iterator(); it.hasNext(); distance++) {
            if (it.next().containsKey(name.lexeme)) {
                locals.put(expr, distance);
                return;
            }
        }
    }

    private void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) resolve(statement);
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void report(Token token, String message) {
        diagnostics.add(new Diagnostic(Diagnostic.Stage.CHECKER, token.line, message));
    }
}
