package codefab.core;

import java.util.List;

/**
 * AST 식 노드의 닫힌 계층(sealed). 대부분 record라 {@code equals}/{@code hashCode}가
 * 구조적(structural)으로 생성된다 — 이름·위치가 같은 두 노드는 서로 같다고 판정된다.
 * 현재 노드를 {@code HashMap} 키 등으로 쓰는 코드는 없어 의도된 트레이드오프다.
 *
 * <p><b>equals 정책</b>: AST 노드를 맵/셋의 키로 쓰지 말 것. 구조적 동등성은
 * {@link Token}의 {@code line}까지 비교에 포함하므로 줄 위치에 의존한다 — 같은 식이라도
 * 줄이 다르면 다른 노드로, 우연히 같은 줄이면 충돌로 취급된다. 또 {@link Variable}/
 * {@link Assign}은 class라 identity 비교가 섞여 일관성도 깨진다. 향후 옵티마이저가
 * 공통 부분식 제거(CSE)·메모이제이션 등으로 노드를 키로 써야 하면
 * {@link java.util.IdentityHashMap}을 쓰거나 해당 노드를 class로 유지하라.
 */
public sealed interface Expr {

    interface Visitor<R> {

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

    <R> R accept(Visitor<R> visitor);

    record Literal(Object value) implements Expr {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteral(this);
        }
    }

    /**
     * 변수 참조. {@code distance}는 Checker가 스코프 깊이를 기록하는 가변 필드라
     * record가 아닌 클래스로 둔다 → Executor O(1) 조회 (Team C 정적 바인딩).
     */
    final class Variable implements Expr {

        public final Token name;
        public int distance = -1;

        public Variable(Token name) {
            this.name = name;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVariable(this);
        }
    }

    /**
     * 대입. {@code distance}가 가변이라 record가 아닌 클래스로 둔다 (Variable 참고).
     */
    final class Assign implements Expr {

        public final Token name;
        public final Expr value;
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

    record Unary(Token operator, Expr right) implements Expr {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnary(this);
        }
    }

    record Binary(Expr left, Token operator, Expr right) implements Expr {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinary(this);
        }
    }

    record Logical(Expr left, Token operator, Expr right) implements Expr {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLogical(this);
        }
    }

    record Grouping(Expr expression) implements Expr {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGrouping(this);
        }
    }

    /** {@code paren}: 닫는 ')' — 런타임 오류 위치 보고용. */
    record Call(Expr callee, Token paren, List<Expr> arguments) implements Expr {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitCall(this);
        }
    }

    /** {@code bracket}: '[' 토큰 — 런타임 오류 위치 보고용. */
    record ArrayGet(Expr array, Expr index, Token bracket) implements Expr {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitArrayGet(this);
        }
    }

    /** {@code bracket}: '[' 토큰 — 런타임 오류 위치 보고용. */
    record ArraySet(Expr array, Expr index, Expr value, Token bracket) implements Expr {
        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitArraySet(this);
        }
    }
}
