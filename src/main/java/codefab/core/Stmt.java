package codefab.core;

import java.util.List;

public sealed interface Stmt {

    interface Visitor<R> {

        R visitExpressionStmt(ExpressionStmt stmt);

        R visitPrintStmt(PrintStmt stmt);

        R visitVarStmt(VarStmt stmt);

        R visitBlockStmt(BlockStmt stmt);

        R visitIfStmt(IfStmt stmt);

        R visitForStmt(ForStmt stmt);

        R visitWhileStmt(WhileStmt stmt);

        R visitFunctionStmt(FunctionStmt stmt);

        R visitReturnStmt(ReturnStmt stmt);
    }

    <R> R accept(Visitor<R> visitor);

    record ExpressionStmt(Expr expression) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    record PrintStmt(Expr expression) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitPrintStmt(this);
        }
    }

    /** {@code initializer}는 nullable (초기값 없는 선언). */
    record VarStmt(Token name, Expr initializer) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStmt(this);
        }
    }

    record BlockStmt(List<Stmt> statements) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }
    }

    /** {@code elseBranch}는 nullable. */
    record IfStmt(Expr condition, Stmt thenBranch, Stmt elseBranch) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }
    }

    /** {@code initializer}/{@code condition}/{@code increment} 모두 nullable. */
    record ForStmt(Stmt initializer, Expr condition, Expr increment, Stmt body) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitForStmt(this);
        }
    }

    record WhileStmt(Expr condition, Stmt body) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStmt(this);
        }
    }

    record FunctionStmt(Token name, List<Token> params, List<Stmt> body) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionStmt(this);
        }
    }

    /**
     * {@code keyword}: 'return' 토큰 — 오류 위치 보고용.
     * {@code value}: nullable (return; 은 null).
     */
    record ReturnStmt(Token keyword, Expr value) implements Stmt {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitReturnStmt(this);
        }
    }
}
