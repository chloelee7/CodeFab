package codefab.core;

import java.util.List;

/**
 * Expression AST nodes. An Expr may hold other Exprs and Tokens as fields, but
 * never a Stmt. Traversal uses the visitor pattern so the Checker and Executor
 * stay decoupled from the node definitions.
 */
public abstract class Expr {
    public interface Visitor<R> {
        R visitLiteral(Literal expr);
        R visitVariable(Variable expr);
        R visitAssign(Assign expr);
        R visitUnary(Unary expr);
        R visitBinary(Binary expr);
        R visitLogical(Logical expr);
        R visitGrouping(Grouping expr);

        /**
         * Function call. Added in the function (Stage 1) work; the Checker and
         * Executor implement it as their visit passes are extended. A default is
         * provided so existing visitors keep compiling until then.
         */
        default R visitCall(Call expr) {
            throw new UnsupportedOperationException("visitCall not implemented");
        }

        /**
         * Array read {@code arr[i]}. Added in the array (Stage 2) work; a default
         * is provided so existing visitors keep compiling until the Checker and
         * Executor override their visit passes.
         */
        default R visitIndex(Index expr) {
            throw new UnsupportedOperationException("visitIndex not implemented");
        }

        /**
         * Array write {@code arr[i] = v}. Added in the array (Stage 2) work; a
         * default is provided so existing visitors keep compiling until the
         * Checker and Executor override their visit passes.
         */
        default R visitIndexSet(IndexSet expr) {
            throw new UnsupportedOperationException("visitIndexSet not implemented");
        }
    }

    public abstract <R> R accept(Visitor<R> visitor);

    public static final class Literal extends Expr {
        public final Object value;

        public Literal(Object value) {
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteral(this);
        }
    }

    public static final class Variable extends Expr {
        public final Token name;

        public Variable(Token name) {
            this.name = name;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVariable(this);
        }
    }

    public static final class Assign extends Expr {
        public final Token name;
        public final Expr value;

        public Assign(Token name, Expr value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitAssign(this);
        }
    }

    public static final class Unary extends Expr {
        public final Token operator;
        public final Expr right;

        public Unary(Token operator, Expr right) {
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnary(this);
        }
    }

    public static final class Binary extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;

        public Binary(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinary(this);
        }
    }

    public static final class Logical extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;

        public Logical(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLogical(this);
        }
    }

    public static final class Grouping extends Expr {
        public final Expr expression;

        public Grouping(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGrouping(this);
        }
    }

    public static final class Call extends Expr {
        public final Expr callee;
        public final Token paren; // closing ')' token, for error line numbers
        public final List<Expr> arguments;

        public Call(Expr callee, Token paren, List<Expr> arguments) {
            this.callee = callee;
            this.paren = paren;
            this.arguments = arguments;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitCall(this);
        }
    }

    public static final class Index extends Expr {
        public final Expr target;
        public final Token bracket; // the '[' token, for error line numbers
        public final Expr index;

        public Index(Expr target, Token bracket, Expr index) {
            this.target = target;
            this.bracket = bracket;
            this.index = index;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIndex(this);
        }
    }

    public static final class IndexSet extends Expr {
        public final Expr target;
        public final Token bracket; // the '[' token, for error line numbers
        public final Expr index;
        public final Expr value;

        public IndexSet(Expr target, Token bracket, Expr index, Expr value) {
            this.target = target;
            this.bracket = bracket;
            this.index = index;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIndexSet(this);
        }
    }
}
