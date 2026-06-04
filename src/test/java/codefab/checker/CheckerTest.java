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
}
