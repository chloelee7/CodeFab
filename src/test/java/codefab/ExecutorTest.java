package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import codefab.core.Expr;
import codefab.core.InterpreterRuntimeError;
import codefab.core.OutputSink;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import codefab.executor.Environment;
import codefab.executor.Executor;

@ExtendWith(MockitoExtension.class)
class ExecutorTest {

    @Mock
    OutputSink output;

    Executor executor;

    @BeforeEach
    void setUp() {
        executor = new Executor(output, Executor.newGlobalScope());
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

    // --- 함수 호출 -----------------------------------------------------------

    @DisplayName("인자 없는 함수 선언 후 호출 — 부작용 실행")
    @Test
    void callsFunctionWithNoArgs() {
        // Func greet() { print "hi"; }  greet();
        Token fnName = tok(TokenType.IDENTIFIER, "greet");
        Token paren  = tok(TokenType.RIGHT_PAREN, ")");
        Stmt fnDecl = new Stmt.FunctionStmt(fnName, List.of(),
            List.of(print(lit("hi"))));
        Stmt call = exprStmt(
            new Expr.Call(new Expr.Variable(fnName), paren, List.of()));
        run(fnDecl, call);
        verify(output).print("hi");
    }

    @DisplayName("return 값이 있는 함수 — 반환값을 print로 출력")
    @Test
    void callsFunctionAndPrintsReturnValue() {
        // Func add(a, b) { return a + b; }  print add(3, 4);
        Token fnName = tok(TokenType.IDENTIFIER, "add");
        Token pA     = tok(TokenType.IDENTIFIER, "a");
        Token pB     = tok(TokenType.IDENTIFIER, "b");
        Token paren  = tok(TokenType.RIGHT_PAREN, ")");
        Token plus   = tok(TokenType.PLUS, "+");

        Expr.Variable aRef = new Expr.Variable(tok(TokenType.IDENTIFIER, "a"));
        Expr.Variable bRef = new Expr.Variable(tok(TokenType.IDENTIFIER, "b"));
        aRef.distance = 0; bRef.distance = 0;

        Stmt.FunctionStmt fn = new Stmt.FunctionStmt(fnName, List.of(pA, pB),
            List.of(new Stmt.ReturnStmt(tok(TokenType.RETURN, "return"),
                new Expr.Binary(aRef, plus, bRef))));

        Expr.Variable fnRef = new Expr.Variable(fnName);
        fnRef.distance = 0;
        Stmt callStmt = print(
            new Expr.Call(fnRef, paren, List.of(lit(3.0), lit(4.0))));
        run(fn, callStmt);
        verify(output).print("7");
    }

    @DisplayName("arity 불일치 시 인자를 평가하기 전에 예외 발생")
    @Test
    void arityMismatchThrowsBeforeEvaluatingArgs() {
        // Func f() { } 를 f(1) 으로 호출
        Token fnName = tok(TokenType.IDENTIFIER, "f");
        Token paren  = tok(TokenType.RIGHT_PAREN, ")");
        Stmt fnDecl  = new Stmt.FunctionStmt(fnName, List.of(), List.of());

        Expr.Variable fnRef = new Expr.Variable(fnName);
        fnRef.distance = 0;
        Stmt call = exprStmt(
            new Expr.Call(fnRef, paren, List.of(lit(1.0))));

        InterpreterRuntimeError error =
            assertThrows(InterpreterRuntimeError.class, () -> run(fnDecl, call));
        assertTrue(error.getMessage().contains("Expected 0 arguments but got 1"));
        // 인자를 평가하지 않았으므로 print 출력 없음
        verify(output, never()).print(anyString());
    }

    @DisplayName("최대 호출 깊이 초과 시 예외 발생")
    @Test
    void exceedsMaxCallDepthThrows() {
        // Func recurse() { recurse(); }  recurse(); → 무한 재귀
        Token fnName = tok(TokenType.IDENTIFIER, "recurse");
        Token paren  = tok(TokenType.RIGHT_PAREN, ")");

        Expr.Variable selfRef = new Expr.Variable(fnName);
        selfRef.distance = -1;
        Stmt.FunctionStmt fn = new Stmt.FunctionStmt(fnName, List.of(),
            List.of(exprStmt(new Expr.Call(selfRef, paren, List.of()))));

        Expr.Variable fnRef = new Expr.Variable(fnName);
        fnRef.distance = -1;
        Stmt call = exprStmt(new Expr.Call(fnRef, paren, List.of()));

        assertThrows(InterpreterRuntimeError.class, () -> run(fn, call));
    }

    // --- 배열 연산 -----------------------------------------------------------

    @DisplayName("배열 생성 후 인덱스 읽기/쓰기")
    @Test
    void arrayCreateReadWrite() {
        codefab.RunResult result = new codefab.CodeFab().run("var a = Array(2); a[0] = 5; print a[0];");
        assertTrue(result.success());
        assertEquals(List.of("5"), result.output());
    }

    @DisplayName("배열 범위 초과 접근 시 예외 발생")
    @Test
    void arrayOutOfBoundsThrows() {
        codefab.RunResult result = new codefab.CodeFab().run("var a = Array(2); print a[5];");
        assertTrue(!result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message.contains("out of bounds")));
    }

    @DisplayName("비정수 크기로 Array() 생성 시 런타임 오류")
    @Test
    void arrayNonIntegerSizeThrows() {
        codefab.RunResult result = new codefab.CodeFab().run("var a = Array(2.9);");
        assertTrue(!result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message.contains("integer")));
    }

    @DisplayName("배열 출력 시 CodeFab 포맷(nil 포함)으로 출력")
    @Test
    void arrayStringifyFormat() {
        codefab.RunResult result = new codefab.CodeFab().run("var a = Array(2); a[0] = 1; print a;");
        assertTrue(result.success());
        assertEquals(List.of("[1, nil]"), result.output());
    }
}
