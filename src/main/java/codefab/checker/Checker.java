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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Checker implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private List<Diagnostic> errors;
    private List<Set<String>> scopes;

    public List<Diagnostic> check(List<Stmt> statements) {
        errors = new ArrayList<>();
        scopes = new ArrayList<>();
        scopes.add(new HashSet<>());
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
        if (stmt.initializer != null) {
            stmt.initializer.accept(this);
        }
        scopes.get(scopes.size() - 1).add(stmt.name.lexeme);
        return null;
    }

    @Override
    public Void visitBlockStmt(BlockStmt stmt) {
        scopes.add(new HashSet<>());
        for (Stmt s : stmt.statements) {
            s.accept(this);
        }
        scopes.remove(scopes.size() - 1);
        return null;
    }

    @Override
    public Void visitIfStmt(IfStmt stmt) {
        return null;
    }

    @Override
    public Void visitForStmt(ForStmt stmt) {
        return null;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private void checkDeclared(Token name) {
        for (int i = 0; i < scopes.size(); i++) {
            if (scopes.get(i).contains(name.lexeme)) return;
        }
        error(name.line, "undefined variable '" + name.lexeme + "'");
    }

    private void error(int line, String message) {
        errors.add(new Diagnostic(Diagnostic.Stage.CHECKER, line, message));
    }
}
