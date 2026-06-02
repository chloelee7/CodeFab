package codefab.core;

import java.util.List;

/**
 * Statement AST nodes. A Stmt may hold Exprs, other Stmts and Tokens as fields.
 * Traversal uses the visitor pattern.
 */
public abstract class Stmt {
    public interface Visitor<R> {
        R visitExpressionStmt(ExpressionStmt stmt);
        R visitPrintStmt(PrintStmt stmt);
        R visitVarStmt(VarStmt stmt);
        R visitBlockStmt(BlockStmt stmt);
        R visitIfStmt(IfStmt stmt);
        R visitForStmt(ForStmt stmt);
    }

    public abstract <R> R accept(Visitor<R> visitor);

    public static final class ExpressionStmt extends Stmt {
        public final Expr expression;

        public ExpressionStmt(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    public static final class PrintStmt extends Stmt {
        public final Expr expression;

        public PrintStmt(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitPrintStmt(this);
        }
    }

    public static final class VarStmt extends Stmt {
        public final Token name;
        public final Expr initializer; // nullable

        public VarStmt(Token name, Expr initializer) {
            this.name = name;
            this.initializer = initializer;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStmt(this);
        }
    }

    public static final class BlockStmt extends Stmt {
        public final List<Stmt> statements;

        public BlockStmt(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }
    }

    public static final class IfStmt extends Stmt {
        public final Expr condition;
        public final Stmt thenBranch;
        public final Stmt elseBranch; // nullable

        public IfStmt(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }
    }

    public static final class ForStmt extends Stmt {
        public final Stmt initializer;  // nullable
        public final Expr condition;    // nullable
        public final Expr increment;    // nullable
        public final Stmt body;

        public ForStmt(Stmt initializer, Expr condition, Expr increment, Stmt body) {
            this.initializer = initializer;
            this.condition = condition;
            this.increment = increment;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitForStmt(this);
        }
    }
}
