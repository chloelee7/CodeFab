package codefab.checker;

import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Expr.Assign;
import codefab.core.Expr.Binary;
import codefab.core.Expr.Grouping;
import codefab.core.Expr.Literal;
import codefab.core.Expr.Logical;
import codefab.core.Expr.Unary;
import codefab.core.Expr.Variable;
import codefab.core.Stmt;
import codefab.core.Stmt.BlockStmt;
import codefab.core.Stmt.ExpressionStmt;
import codefab.core.Stmt.ForStmt;
import codefab.core.Stmt.IfStmt;
import codefab.core.Stmt.PrintStmt;
import codefab.core.Stmt.VarStmt;
import codefab.core.Token;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Checker implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private List<Diagnostic> errors;
    private Deque<Set<String>> scopes;

    public List<Diagnostic> check(List<Stmt> statements) {
        errors = new ArrayList<>();
        scopes = new ArrayDeque<>();
        scopes.push(new HashSet<>());
        for (Stmt stmt : statements) {
            stmt.accept(this);
        }
        return errors;
    }

    // ── Expr visitors ──────────────────────────────────────────────────────────

    @Override
    public Void visitLiteral(Literal expr) {
        return null;
    }

    @Override
    public Void visitVariable(Variable expr) {
        checkDeclared(expr.name);
        return null;
    }

    @Override
    public Void visitAssign(Assign expr) {
        expr.value.accept(this);
        checkDeclared(expr.name);
        return null;
    }

    @Override
    public Void visitUnary(Unary expr) {
        return null;
    }

    @Override
    public Void visitBinary(Binary expr) {
        expr.left.accept(this);
        expr.right.accept(this);
        return null;
    }

    @Override
    public Void visitLogical(Logical expr) {
        return null;
    }

    @Override
    public Void visitGrouping(Grouping expr) {
        return null;
    }

    // ── Stmt visitors ──────────────────────────────────────────────────────────

    @Override
    public Void visitExpressionStmt(ExpressionStmt stmt) {
        stmt.expression.accept(this);
        return null;
    }

    @Override
    public Void visitPrintStmt(PrintStmt stmt) {
        stmt.expression.accept(this);
        return null;
    }

    @Override
    public Void visitVarStmt(VarStmt stmt) {
        visitIfPresent(stmt.initializer);
        scopes.peek().add(stmt.name.lexeme);
        return null;
    }

    @Override
    public Void visitBlockStmt(BlockStmt stmt) {
        scopes.push(new HashSet<>());
        for (Stmt s : stmt.statements) {
            s.accept(this);
        }
        scopes.pop();
        return null;
    }

    @Override
    public Void visitIfStmt(IfStmt stmt) {
        stmt.condition.accept(this);
        stmt.thenBranch.accept(this);
        visitIfPresent(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitForStmt(ForStmt stmt) {
        scopes.push(new HashSet<>());
        visitIfPresent(stmt.initializer);
        visitIfPresent(stmt.condition);
        stmt.body.accept(this);
        visitIfPresent(stmt.increment);
        scopes.pop();
        return null;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private void visitIfPresent(Expr expr) {
        if (expr != null) expr.accept(this);
    }

    private void visitIfPresent(Stmt stmt) {
        if (stmt != null) stmt.accept(this);
    }

    private void checkDeclared(Token name) {
        for (Set<String> scope : scopes) {
            if (scope.contains(name.lexeme)) return;
        }
        error(name.line, "undefined variable '" + name.lexeme + "'");
    }

    private void error(int line, String message) {
        errors.add(new Diagnostic(Diagnostic.Stage.CHECKER, line, message));
    }
}
