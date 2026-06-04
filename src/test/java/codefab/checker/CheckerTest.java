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
