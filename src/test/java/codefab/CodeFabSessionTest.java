package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodeFabSessionTest {

    private CodeFabSession session;

    @BeforeEach
    void setUp() {
        session = new CodeFabSession();
    }

    @Test
    @DisplayName("변수는 여러 입력에 걸쳐 유지된다")
    void variablesPersistAcrossMultipleInputs() {
        assertTrue(session.run("var a = 5;").success());
        assertTrue(session.run("var b = 10;").success());
        RunResult r = session.run("print a + b;");
        assertTrue(r.success(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(List.of("15"), r.output());
    }

    @Test
    @DisplayName("각 실행은 자신의 출력만 보고한다")
    void eachRunReportsOwnOutputOnly() {
        session.run("var x = 1;");
        RunResult r = session.run("print x;");
        assertEquals(List.of("1"), r.output());
    }

    @Test
    @DisplayName("REPL에서 입력 간 기존 전역 변수 재선언이 허용된다")
    void redeclaringGlobalVariableAcrossRunsIsAllowed() {
        assertTrue(session.run("var a = 1;").success());
        RunResult r = session.run("var a = 2; print a;");
        assertTrue(r.success(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(List.of("2"), r.output());
    }

    @Test
    @DisplayName("한 입력의 런타임 오류가 세션을 죽이지 않는다")
    void runtimeErrorInOneRunDoesNotKillSession() {
        session.run("var a = 1;");
        assertFalse(session.run("print missing;").success());
        RunResult r = session.run("print a;");
        assertTrue(r.success());
        assertEquals(List.of("1"), r.output());
    }
}
