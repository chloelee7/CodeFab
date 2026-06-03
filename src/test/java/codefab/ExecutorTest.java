package codefab;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import codefab.core.Expr;
import codefab.core.OutputSink;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import codefab.executor.Environment;
import codefab.executor.Executor;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class ExecutorTest {

    @Mock
    OutputSink output;

    Executor executor;

    @BeforeEach
    void setUp() {
        executor = new Executor(output, new Environment());
    }

    private static Token tok(TokenType type, String lexeme) {
        return new Token(type, lexeme, null, 1);
    }

    private static Expr lit(Object value) {
        return new Expr.Literal(value);
    }

    private static Stmt print(Expr expr) {
        return new Stmt.PrintStmt(expr);
    }

    private static Stmt exprStmt(Expr expr) {
        return new Stmt.ExpressionStmt(expr);
    }

    private void run(Stmt... statements) {
        executor.execute(List.of(statements));
    }

    // --- stage 1: print + literal stringify -------------------------------

    @DisplayName("정수형 숫자 출력")
    @Test
    void printsIntegralNumberWithoutDecimalPoint() {
        run(print(lit(42.0)));
        verify(output).print("42");
    }

    @DisplayName("소수점 숫자 출력")
    @Test
    void printsFractionalNumberWithDecimalPoint() {
        run(print(lit(1.5)));
        verify(output).print("1.5");
    }

    @DisplayName("문자열 출력")
    @Test
    void printsStringValueVerbatim() {
        run(print(lit("hello")));
        verify(output).print("hello");
    }

    @DisplayName("불리언 출력")
    @Test
    void printsBooleanValue() {
        run(print(lit(true)));
        verify(output).print("true");
    }
}
