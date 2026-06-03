package codefab;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import codefab.core.InterpreterRuntimeError;

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

    @DisplayName("null→nil 출력")
    @Test
    void printsNilForNullValue() {
        run(print(lit(null)));
        verify(output).print("nil");
    }

    @DisplayName("표현식 문장 출력 없음")
    @Test
    void expressionStatementProducesNoOutput() {
        run(exprStmt(lit(99.0)));
        verify(output, never()).print(anyString());
    }

    // --- stage 2: variable declaration / lookup ----------------------------

    @DisplayName("변수 선언 및 읽기")
    @Test
    void declaresAndReadsInitializedVariable() {
        Stmt declare = new Stmt.VarStmt(tok(TokenType.IDENTIFIER, "x"), lit(7.0));
        Stmt show = print(new Expr.Variable(tok(TokenType.IDENTIFIER, "x")));
        run(declare, show);
        verify(output).print("7");
    }

    @DisplayName("미초기화 변수 nil 보유")
    @Test
    void uninitializedVariableHoldsNil() {
        Stmt declare = new Stmt.VarStmt(tok(TokenType.IDENTIFIER, "x"), null);
        Stmt show = print(new Expr.Variable(tok(TokenType.IDENTIFIER, "x")));
        run(declare, show);
        verify(output).print("nil");
    }

    @DisplayName("미정의 변수 예외")
    @Test
    void readingUndefinedVariableThrows() {
        Stmt show = print(new Expr.Variable(tok(TokenType.IDENTIFIER, "missing")));
        InterpreterRuntimeError error =
            assertThrows(InterpreterRuntimeError.class, () -> run(show));
        assertTrue(error.getMessage().contains("Undefined variable"));
    }
}
