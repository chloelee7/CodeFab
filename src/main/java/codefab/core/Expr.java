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
        R visitCall(Call expr);
        R visitArrayGet(ArrayGet expr);
        R visitArraySet(ArraySet expr);
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
        // Checker가 스코프 깊이를 기록 → Executor O(1) 조회 (Team C 정적 바인딩)
        public int distance = -1;

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
        // Checker가 스코프 깊이를 기록 → Executor O(1) 조회 (Team C 정적 바인딩)
        public int distance = -1;

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
        public final Token paren;          // 닫는 ')' — 런타임 오류 위치 보고용
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

    public static final class ArrayGet extends Expr {
        public final Expr array;
        public final Expr index;
        public final Token bracket;        // '[' 토큰 — 런타임 오류 위치 보고용

        public ArrayGet(Expr array, Expr index, Token bracket) {
            this.array = array;
            this.index = index;
            this.bracket = bracket;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitArrayGet(this);
        }
    }

    public static final class ArraySet extends Expr {
        public final Expr array;
        public final Expr index;
        public final Expr value;
        public final Token bracket;        // '[' 토큰 — 런타임 오류 위치 보고용

        public ArraySet(Expr array, Expr index, Expr value, Token bracket) {
            this.array = array;
            this.index = index;
            this.value = value;
            this.bracket = bracket;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitArraySet(this);
        }
    }
}
