package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codefab.core.Diagnostic;
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

    // ── builtin 스코프 분리 불변식: 네이티브 함수는 사용자 대입으로 오염되지 않는다 ──────────
    // 쓰기는 builtin 경계에서 멈추고(읽기만 builtin까지 허용), bare 미선언 대입은
    // 네이티브를 덮지 못하고 RUNTIME `Undefined variable '<name>'.`로 실패한다(shared-contracts §4/§7).

    @Test
    @DisplayName("bare 미선언 대입은 네이티브를 오염시키지 못하고 미정의 변수 오류로 실패한다")
    void bareAssignmentToNativeNameFailsWithoutPollutingBuiltin() {
        RunResult r = session.run("chr = 5;");
        assertFalse(r.success(), () -> "var 없는 미선언 대입은 실패해야 한다, output: " + r.output());
        assertTrue(r.diagnostics().stream()
                        .anyMatch(d -> d.stage == Diagnostic.Stage.RUNTIME
                                && d.message.contains("Undefined variable 'chr'.")),
                () -> "RUNTIME Undefined variable 'chr'.를 보고해야 한다, diagnostics: " + r.diagnostics());
    }

    @Test
    @DisplayName("실패한 네이티브 이름 대입 후에도 다른 run에서 네이티브 호출이 보존된다")
    void failedAssignmentToNativeNamePreservesNativeAcrossRuns() {
        RunResult r1 = session.run("chr = 5;");
        assertFalse(r1.success(), () -> "첫 run은 실패해야 한다, output: " + r1.output());

        RunResult r2 = session.run("print chr(65);");
        assertTrue(r2.success(), () -> "네이티브 chr 호출은 보존되어야 한다, diagnostics: " + r2.diagnostics());
        assertEquals(List.of("A"), r2.output());
    }

    @Test
    @DisplayName("네이티브 이름을 사용자 전역으로 선언하면 재대입(셰도잉)이 정상 동작한다")
    void userGlobalShadowingNativeNameAllowsReassignment() {
        RunResult r = session.run("var chr = 99; chr = 5; print chr;");
        assertTrue(r.success(), () -> "셰도잉 후 재대입은 성공해야 한다, diagnostics: " + r.diagnostics());
        assertEquals(List.of("5"), r.output());
    }
}
