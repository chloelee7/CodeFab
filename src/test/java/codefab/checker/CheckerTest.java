package codefab.checker;

import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CheckerTest {

    private final Checker checker = new Checker();

    @Test
    @DisplayName("변수 선언만 있을 때 에러가 없다")
    void 변수_선언만_있을_때_에러가_없다() {
        // given: var a = 3;
        Token nameToken = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Stmt.VarStmt varStmt = new Stmt.VarStmt(nameToken, new Expr.Literal(3.0));

        // when
        List<Diagnostic> result = checker.check(List.of(varStmt));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("선언된 변수를 print로 참조할 때 에러가 없다")
    void 선언된_변수를_print로_참조할_때_에러가_없다() {
        // given: var a = 3; print a;
        Token aDecl = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Token aRef  = new Token(TokenType.IDENTIFIER, "a", null, 2);

        Stmt.VarStmt varStmt   = new Stmt.VarStmt(aDecl, new Expr.Literal(3.0));
        Stmt.PrintStmt printStmt = new Stmt.PrintStmt(new Expr.Variable(aRef));

        // when
        List<Diagnostic> result = checker.check(List.of(varStmt, printStmt));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("선언되지 않은 변수를 print로 참조하면 CHECKER 에러가 1개 발생한다")
    void 선언되지_않은_변수를_print로_참조하면_CHECKER_에러가_1개_발생한다() {
        // given: print x;
        Token xRef = new Token(TokenType.IDENTIFIER, "x", null, 1);
        Stmt.PrintStmt printStmt = new Stmt.PrintStmt(new Expr.Variable(xRef));

        // when
        List<Diagnostic> result = checker.check(List.of(printStmt));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stage).isEqualTo(Diagnostic.Stage.CHECKER);
    }

    @Test
    @DisplayName("if 조건식과 then 블록에서 선언된 변수를 참조할 때 에러가 없다")
    void if_조건식과_then_블록에서_선언된_변수를_참조할_때_에러가_없다() {
        // given: var x = 5; if (x > 0) { print x; }
        Token xDecl  = new Token(TokenType.IDENTIFIER, "x", null, 1);
        Token xCond  = new Token(TokenType.IDENTIFIER, "x", null, 2);
        Token xPrint = new Token(TokenType.IDENTIFIER, "x", null, 3);
        Token gt     = new Token(TokenType.GREATER, ">", null, 2);

        Stmt.VarStmt varStmt     = new Stmt.VarStmt(xDecl, new Expr.Literal(5.0));
        Expr.Binary  condition   = new Expr.Binary(new Expr.Variable(xCond), gt, new Expr.Literal(0.0));
        Stmt.BlockStmt thenBlock = new Stmt.BlockStmt(List.of(
                new Stmt.PrintStmt(new Expr.Variable(xPrint))));
        Stmt.IfStmt ifStmt = new Stmt.IfStmt(condition, thenBlock, null);

        // when
        List<Diagnostic> result = checker.check(List.of(varStmt, ifStmt));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("if 조건식과 then 블록에서 미선언 변수를 참조하면 CHECKER 에러가 발생한다")
    void if_조건식과_then_블록에서_미선언_변수를_참조하면_CHECKER_에러가_발생한다() {
        // given: if (z > 0) { print z; }
        Token zCond  = new Token(TokenType.IDENTIFIER, "z", null, 1);
        Token zPrint = new Token(TokenType.IDENTIFIER, "z", null, 2);
        Token gt     = new Token(TokenType.GREATER, ">", null, 1);

        Expr.Binary  condition   = new Expr.Binary(new Expr.Variable(zCond), gt, new Expr.Literal(0.0));
        Stmt.BlockStmt thenBlock = new Stmt.BlockStmt(List.of(
                new Stmt.PrintStmt(new Expr.Variable(zPrint))));
        Stmt.IfStmt ifStmt = new Stmt.IfStmt(condition, thenBlock, null);

        // when
        List<Diagnostic> result = checker.check(List.of(ifStmt));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(d -> d.stage == Diagnostic.Stage.CHECKER);
    }

    @Test
    @DisplayName("단항 연산식에서 미선언 변수를 참조하면 CHECKER 에러가 발생한다")
    void 단항_연산식에서_미선언_변수를_참조하면_CHECKER_에러가_발생한다() {
        // given: !x  (x 미선언)
        Token bang = new Token(TokenType.BANG, "!", null, 1);
        Token xRef = new Token(TokenType.IDENTIFIER, "x", null, 1);
        Stmt.ExpressionStmt stmt = new Stmt.ExpressionStmt(
                new Expr.Unary(bang, new Expr.Variable(xRef)));

        // when
        List<Diagnostic> result = checker.check(List.of(stmt));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stage).isEqualTo(Diagnostic.Stage.CHECKER);
    }

    @Test
    @DisplayName("논리 연산식에서 미선언 변수를 참조하면 CHECKER 에러가 발생한다")
    void 논리_연산식에서_미선언_변수를_참조하면_CHECKER_에러가_발생한다() {
        // given: a and b  (a, b 미선언)
        Token and  = new Token(TokenType.AND, "and", null, 1);
        Token aRef = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Token bRef = new Token(TokenType.IDENTIFIER, "b", null, 1);
        Stmt.ExpressionStmt stmt = new Stmt.ExpressionStmt(
                new Expr.Logical(new Expr.Variable(aRef), and, new Expr.Variable(bRef)));

        // when
        List<Diagnostic> result = checker.check(List.of(stmt));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(d -> d.stage == Diagnostic.Stage.CHECKER);
    }

    @Test
    @DisplayName("그룹핑 식에서 미선언 변수를 참조하면 CHECKER 에러가 발생한다")
    void 그룹핑_식에서_미선언_변수를_참조하면_CHECKER_에러가_발생한다() {
        // given: (x + 1)  (x 미선언)
        Token plus = new Token(TokenType.PLUS, "+", null, 1);
        Token xRef = new Token(TokenType.IDENTIFIER, "x", null, 1);
        Stmt.ExpressionStmt stmt = new Stmt.ExpressionStmt(
                new Expr.Grouping(
                        new Expr.Binary(new Expr.Variable(xRef), plus, new Expr.Literal(1.0))));

        // when
        List<Diagnostic> result = checker.check(List.of(stmt));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stage).isEqualTo(Diagnostic.Stage.CHECKER);
    }

    @Test
    @DisplayName("for 루프에서 선언된 변수를 조건식·증감식·body에서 참조할 때 에러가 없다")
    void for_루프에서_선언된_변수를_조건식_증감식_body에서_참조할_때_에러가_없다() {
        // given: for (var i = 0; i < 3; i = i + 1) { print i; }
        Token iDecl   = new Token(TokenType.IDENTIFIER, "i", null, 1);
        Token iCond   = new Token(TokenType.IDENTIFIER, "i", null, 1);
        Token iIncLhs = new Token(TokenType.IDENTIFIER, "i", null, 1);
        Token iIncRhs = new Token(TokenType.IDENTIFIER, "i", null, 1);
        Token iPrint  = new Token(TokenType.IDENTIFIER, "i", null, 2);
        Token less    = new Token(TokenType.LESS, "<", null, 1);
        Token plus    = new Token(TokenType.PLUS, "+", null, 1);

        Stmt.VarStmt   init      = new Stmt.VarStmt(iDecl, new Expr.Literal(0.0));
        Expr.Binary    condition = new Expr.Binary(new Expr.Variable(iCond), less, new Expr.Literal(3.0));
        Expr.Assign    increment = new Expr.Assign(iIncLhs,
                new Expr.Binary(new Expr.Variable(iIncRhs), plus, new Expr.Literal(1.0)));
        Stmt.BlockStmt body      = new Stmt.BlockStmt(List.of(
                new Stmt.PrintStmt(new Expr.Variable(iPrint))));
        Stmt.ForStmt forStmt = new Stmt.ForStmt(init, condition, increment, body);

        // when
        List<Diagnostic> result = checker.check(List.of(forStmt));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("for 루프 body에서 미선언 변수를 참조하면 CHECKER 에러가 발생한다")
    void for_루프_body에서_미선언_변수를_참조하면_CHECKER_에러가_발생한다() {
        // given: for (var i = 0; i < 3; i = i + 1) { print z; }
        Token iDecl   = new Token(TokenType.IDENTIFIER, "i", null, 1);
        Token iCond   = new Token(TokenType.IDENTIFIER, "i", null, 1);
        Token iIncLhs = new Token(TokenType.IDENTIFIER, "i", null, 1);
        Token iIncRhs = new Token(TokenType.IDENTIFIER, "i", null, 1);
        Token zPrint  = new Token(TokenType.IDENTIFIER, "z", null, 2);
        Token less    = new Token(TokenType.LESS, "<", null, 1);
        Token plus    = new Token(TokenType.PLUS, "+", null, 1);

        Stmt.VarStmt   init      = new Stmt.VarStmt(iDecl, new Expr.Literal(0.0));
        Expr.Binary    condition = new Expr.Binary(new Expr.Variable(iCond), less, new Expr.Literal(3.0));
        Expr.Assign    increment = new Expr.Assign(iIncLhs,
                new Expr.Binary(new Expr.Variable(iIncRhs), plus, new Expr.Literal(1.0)));
        Stmt.BlockStmt body      = new Stmt.BlockStmt(List.of(
                new Stmt.PrintStmt(new Expr.Variable(zPrint))));
        Stmt.ForStmt forStmt = new Stmt.ForStmt(init, condition, increment, body);

        // when
        List<Diagnostic> result = checker.check(List.of(forStmt));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stage).isEqualTo(Diagnostic.Stage.CHECKER);
    }

    @Test
    @DisplayName("블록 안에서 선언된 변수를 블록 안에서 참조할 때 에러가 없다")
    void 블록_안에서_선언된_변수를_블록_안에서_참조할_때_에러가_없다() {
        // given: { var a = 1; print a; }
        Token aDecl = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Token aRef  = new Token(TokenType.IDENTIFIER, "a", null, 2);

        Stmt.BlockStmt block = new Stmt.BlockStmt(List.of(
                new Stmt.VarStmt(aDecl, new Expr.Literal(1.0)),
                new Stmt.PrintStmt(new Expr.Variable(aRef))
        ));

        // when
        List<Diagnostic> result = checker.check(List.of(block));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("블록 안에서 선언된 변수를 블록 밖에서 참조하면 CHECKER 에러가 1개 발생한다")
    void 블록_안에서_선언된_변수를_블록_밖에서_참조하면_CHECKER_에러가_1개_발생한다() {
        // given: { var a = 1; } print a;
        Token aDecl = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Token aRef  = new Token(TokenType.IDENTIFIER, "a", null, 3);

        Stmt.BlockStmt block    = new Stmt.BlockStmt(List.of(
                new Stmt.VarStmt(aDecl, new Expr.Literal(1.0))
        ));
        Stmt.PrintStmt printStmt = new Stmt.PrintStmt(new Expr.Variable(aRef));

        // when
        List<Diagnostic> result = checker.check(List.of(block, printStmt));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stage).isEqualTo(Diagnostic.Stage.CHECKER);
    }

    @Test
    @DisplayName("선언된 변수에 재할당할 때 에러가 없다")
    void 선언된_변수에_재할당할_때_에러가_없다() {
        // given: var a = 3; a = a + 1;
        Token aDecl = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Token aLhs  = new Token(TokenType.IDENTIFIER, "a", null, 2);
        Token aRhs  = new Token(TokenType.IDENTIFIER, "a", null, 2);
        Token plus  = new Token(TokenType.PLUS, "+", null, 2);

        Stmt.VarStmt varStmt = new Stmt.VarStmt(aDecl, new Expr.Literal(3.0));
        Expr.Binary rhs      = new Expr.Binary(new Expr.Variable(aRhs), plus, new Expr.Literal(1.0));
        Expr.Assign assign   = new Expr.Assign(aLhs, rhs);
        Stmt.ExpressionStmt exprStmt = new Stmt.ExpressionStmt(assign);

        // when
        List<Diagnostic> result = checker.check(List.of(varStmt, exprStmt));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("선언되지 않은 변수에 할당하면 CHECKER 에러가 1개 발생한다")
    void 선언되지_않은_변수에_할당하면_CHECKER_에러가_1개_발생한다() {
        // given: b = 5;
        Token bToken = new Token(TokenType.IDENTIFIER, "b", null, 1);
        Expr.Assign assign   = new Expr.Assign(bToken, new Expr.Literal(5.0));
        Stmt.ExpressionStmt exprStmt = new Stmt.ExpressionStmt(assign);

        // when
        List<Diagnostic> result = checker.check(List.of(exprStmt));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stage).isEqualTo(Diagnostic.Stage.CHECKER);
    }

    @Test
    @DisplayName("초기화 식에서 선언 중인 변수를 자기 참조하면 CHECKER 에러가 1개 발생한다")
    void 초기화_식에서_선언_중인_변수를_자기_참조하면_CHECKER_에러가_1개_발생한다() {
        // given: var a = a + 1;
        Token nameToken = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Token aRefToken = new Token(TokenType.IDENTIFIER, "a", null, 1);
        Token plusToken = new Token(TokenType.PLUS, "+", null, 1);

        Expr.Variable aRef = new Expr.Variable(aRefToken);
        Expr.Binary binary = new Expr.Binary(aRef, plusToken, new Expr.Literal(1.0));
        Stmt.VarStmt varStmt = new Stmt.VarStmt(nameToken, binary);

        // when
        List<Diagnostic> result = checker.check(List.of(varStmt));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stage).isEqualTo(Diagnostic.Stage.CHECKER);
    }
}
