package codefab.checker;

import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static semantic analysis over the AST, performed by DFS before execution.
 *
 * <p>It detects two classes of error and nothing else (syntax is the Parser's
 * job, type/runtime faults are the Executor's): declaring two variables with
 * the same name in one scope, and reading a variable inside its own
 * initializer. It never executes code.
 */
public final class Checker implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    /** A name is DECLARED once its statement is seen, DEFINED once its initializer is checked. */
    private enum VarState { DECLARED, DEFINED }

    private final List<Diagnostic> diagnostics;
    private final Deque<Map<String, VarState>> scopes = new ArrayDeque<>();

    public Checker(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
    }

    /** Check a whole program. A top-level scope models global declarations. */
    public void check(List<Stmt> statements) {
        beginScope();
        resolve(statements);
        endScope();
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
        return null;
    }

    @Override
    public Void visitAssign(Expr.Assign expr) {
        resolve(expr.value);
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
