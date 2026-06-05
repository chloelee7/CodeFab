package codefab.core;

import java.util.List;

/**
 * Statement AST nodes. A Stmt may hold Exprs, other Stmts and Tokens as fields.
 * Traversal uses the visitor pattern.
 *
 * <p>Every Stmt records the source {@code line} of its starting token. The debug
 * mode and file-mode runtime errors require a line number per statement
 * (contract §3). The field is set by the Parser at construction time and does
 * not affect the visitor signatures.
 */
public abstract class Stmt {
    /** Source line of this statement's starting token. */
    public final int line;

    protected Stmt(int line) {
        this.line = line;
    }

    public interface Visitor<R> {
        R visitExpressionStmt(ExpressionStmt stmt);
        R visitPrintStmt(PrintStmt stmt);
        R visitVarStmt(VarStmt stmt);
        R visitBlockStmt(BlockStmt stmt);
        R visitIfStmt(IfStmt stmt);
        R visitForStmt(ForStmt stmt);
        R visitWhileStmt(WhileStmt stmt);

        /**
         * Function declaration. Added in the function (Stage 1) work; the Checker
         * and Executor implement it as their visit passes are extended. A default
         * is provided so existing visitors keep compiling until then.
         */
        default R visitFunctionStmt(FunctionStmt stmt) {
            throw new UnsupportedOperationException("visitFunctionStmt not implemented");
        }

        /**
         * Return statement. Added in the function (Stage 1) work; the Checker and
         * Executor implement it as their visit passes are extended. A default is
         * provided so existing visitors keep compiling until then.
         */
        default R visitReturnStmt(ReturnStmt stmt) {
            throw new UnsupportedOperationException("visitReturnStmt not implemented");
        }
    }

    public abstract <R> R accept(Visitor<R> visitor);

    public static final class ExpressionStmt extends Stmt {
        public final Expr expression;

        public ExpressionStmt(int line, Expr expression) {
            super(line);
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    public static final class PrintStmt extends Stmt {
        public final Expr expression;

        public PrintStmt(int line, Expr expression) {
            super(line);
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

        public VarStmt(int line, Token name, Expr initializer) {
            super(line);
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

        public BlockStmt(int line, List<Stmt> statements) {
            super(line);
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

        public IfStmt(int line, Expr condition, Stmt thenBranch, Stmt elseBranch) {
            super(line);
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

        public ForStmt(int line, Stmt initializer, Expr condition, Expr increment, Stmt body) {
            super(line);
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

    public static final class WhileStmt extends Stmt {
        public final Expr condition;
        public final Stmt body;

        public WhileStmt(int line, Expr condition, Stmt body) {
            super(line);
            this.condition = condition;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStmt(this);
        }
    }

    public static final class FunctionStmt extends Stmt {
        public final Token name;
        public final List<Token> params; // may be empty
        public final List<Stmt> body;

        public FunctionStmt(int line, Token name, List<Token> params, List<Stmt> body) {
            super(line);
            this.name = name;
            this.params = params;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionStmt(this);
        }
    }

    public static final class ReturnStmt extends Stmt {
        public final Token keyword;
        public final Expr value; // nullable: `return;` yields a null value

        public ReturnStmt(int line, Token keyword, Expr value) {
            super(line);
            this.keyword = keyword;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitReturnStmt(this);
        }
    }
}
