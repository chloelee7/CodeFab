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

import java.util.ArrayList;
import java.util.List;

public class Checker implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private List<Diagnostic> errors = new ArrayList<>();

    public List<Diagnostic> check(List<Stmt> statements) {
        errors = new ArrayList<>();
        for (Stmt stmt : statements) {
            stmt.accept(this);
        }
        return errors;
    }

    @Override
    public Void visitLiteral(Literal expr) {
        return null;
    }

    @Override
    public Void visitVariable(Variable expr) {
        return null;
    }

    @Override
    public Void visitAssign(Assign expr) {
        return null;
    }

    @Override
    public Void visitUnary(Unary expr) {
        return null;
    }

    @Override
    public Void visitBinary(Binary expr) {
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

    @Override
    public Void visitExpressionStmt(ExpressionStmt stmt) {
        return null;
    }

    @Override
    public Void visitPrintStmt(PrintStmt stmt) {
        return null;
    }

    @Override
    public Void visitVarStmt(VarStmt stmt) {
        return null;
    }

    @Override
    public Void visitBlockStmt(BlockStmt stmt) {
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
}
