package codefab;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * builtin 스코프 분리 (스펙 06): 네이티브 함수는 globals 바깥의 builtin 스코프에 등록되며,
 * 디버그 inspect/덤프는 builtin 경계에서 멈춰 네이티브 함수를 노출하지 않는다(shared-contracts §8-0, §10).
 *
 * <p>builtin 스코프 분리 후 동작을 고정한다: 네이티브 함수는 더 이상 globals에 노출되지 않으며
 * 디버그 inspect는 builtin 경계에서 멈춘다. 검증은 모두 <b>사용자 관찰 가능한 디버그 출력</b>으로만
 * 한다 — Executor.newGlobalScope 같은 내부 API에 직접 의존하지 않는다.
 */
@DisplayName("builtin 스코프 분리")
class BuiltinScopeTest {

    /** shared-contracts §8-0 / §8에 명시된 네이티브 함수 이름 전체. */
    private static final List<String> NATIVE_NAMES = List.of(
            "chr", "charAt", "Array", "len", "slice", "push",
            "num", "ord", "typeOf", "valueText");

    @TempDir
    Path tempDir;

    /** DebugShellTestSupport.drive로 위임한다(DebugShellTest와 공용 헬퍼 공유). */
    private String drive(String source, String commands) throws IOException {
        return DebugShellTestSupport.drive(tempDir, source, commands);
    }

    /** inspect 출력 영역에서 "<scope> <name> = ..." 형태로 노출된 변수명을 찾는다. */
    private boolean inspectExposesVariable(String output, String name) {
        for (String line : output.split("\\R")) {
            // inspect 라인은 "[로컬] foo = ..." / "[전역] foo = ..." (DebugShell.doInspect 포맷)
            // 프롬프트 prefix("> ")가 붙을 수 있으므로 제거 후 검사.
            String trimmed = line.startsWith("> ") ? line.substring(2) : line;
            if (trimmed.startsWith("[로컬] " + name + " = ")
                    || trimmed.startsWith("[전역] " + name + " = ")) {
                return true;
            }
        }
        return false;
    }

    // ── 1. inspect 네이티브 제외 ────────────────────────────────────────────────

    @Test
    @DisplayName("inspect는 네이티브 함수를 노출하지 않고 사용자 전역 변수만 보여준다")
    void inspectExcludesNativeFunctionsAndShowsOnlyUserVariables() throws IOException {
        // 사용자 전역 변수 두 개를 선언하고, 마지막에 멈출 문장(print)을 둔다.
        // step 두 번이면 두 var가 모두 정의된 뒤 print 문 직전에서 정지 → 그 시점에 inspect.
        String source = String.join("\n",
                "var width = 10;",
                "var label = \"panel\";",
                "print width;",
                "");

        String output = drive(source, "step\nstep\ninspect\nexit\n");

        // 사용자 변수는 보여야 한다.
        assertTrue(inspectExposesVariable(output, "width"),
                () -> "사용자 변수 width가 inspect에 보여야 한다:\n" + output);
        assertTrue(inspectExposesVariable(output, "label"),
                () -> "사용자 변수 label이 inspect에 보여야 한다:\n" + output);

        // 네이티브 함수 이름은 단 하나도 노출되면 안 된다(builtin 경계에서 멈춤).
        for (String native_ : NATIVE_NAMES) {
            assertFalse(inspectExposesVariable(output, native_),
                    () -> "네이티브 함수 '" + native_ + "'가 inspect에 노출되면 안 된다:\n" + output);
        }
    }

    @Test
    @DisplayName("사용자 변수가 없으면 inspect는 네이티브 함수가 아니라 '변수 없음'을 보고한다")
    void inspectReportsNoVariablesWhenOnlyNativesExistInScope() throws IOException {
        // 아무 변수도 선언하지 않는 소스. 첫 문장 정지 시점에 inspect 하면
        // builtin(네이티브) 외에는 변수가 없어야 한다.
        String source = String.join("\n",
                "print 1;",
                "");

        String output = drive(source, "inspect\nstep\nexit\n");

        for (String native_ : NATIVE_NAMES) {
            assertFalse(inspectExposesVariable(output, native_),
                    () -> "네이티브 함수 '" + native_ + "'가 inspect에 노출되면 안 된다:\n" + output);
        }
        assertTrue(output.contains("(변수 없음)"),
                () -> "사용자 변수가 없으면 '(변수 없음)'을 보고해야 한다:\n" + output);
    }

    // ── 2. 네이티브 함수 호출 회귀 ──────────────────────────────────────────────
    // 광범위한 네이티브 동작 회귀는 NativeFunctionTest가 이미 커버한다(chr/len/charAt/slice/push/
    // num/ord/typeOf/valueText/Array). 여기서는 builtin 스코프 분리 후에도 핵심 호출이
    // 여전히 동작함을 한 번에 묶어 확인하는 가드만 둔다(중복 최소화).

    @Test
    @DisplayName("builtin 스코프 분리 후에도 네이티브 함수 호출이 정상 동작한다")
    void nativeFunctionsStillCallableAfterScopeSeparation() {
        String source = String.join("\n",
                "print chr(65);",            // "A"
                "print len(\"ab\");",        // 2
                "print charAt(\"hi\", 0);",  // "h"
                "var a = Array(3);",
                "print len(a);",             // 3
                "");

        RunResult result = new CodeFab().run(source);
        assertTrue(result.success(),
                () -> "네이티브 호출은 성공해야 한다, diagnostics: " + result.diagnostics());
        assertEquals(List.of("A", "2", "h", "3"), result.output());
    }

    // ── 3. 전역 셰도잉 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("사용자가 chr를 전역에 선언하면 그 값이 builtin을 가리고 inspect엔 사용자 chr만 보인다")
    void userGlobalShadowsNativeAndInspectShowsOnlyUserValue() throws IOException {
        // 사용자가 네이티브 이름 chr를 전역 변수로 선언. globals가 builtin보다 안쪽이라 가린다.
        String source = String.join("\n",
                "var chr = 99;",
                "print chr;",
                "print chr;",
                "");

        // step 두 번이면 var chr 정의 + 첫 print 실행 후 두 번째 print 직전에서 정지 → inspect.
        String output = drive(source, "step\nstep\ninspect\nexit\n");

        // 사용자 chr(값 99)가 inspect에 노출되어야 한다.
        // 스펙 본문은 "[전역]"이라 적지만, 현재 디버그 세션은 최상위 사용자 변수를 [로컬]로 라벨한다
        // (기존 DebugShellTest.inspectDumpsScopeVariables도 스코프 라벨을 단언하지 않는다).
        // 이 테스트의 load-bearing 단언은 "사용자 chr=99가 보이고, 네이티브 chr는 안 보인다"이므로
        // 스코프 라벨([로컬]/[전역])은 둘 다 허용한다. 라벨 확정은 엔지니어와 합의 필요(아래 보고 참조).
        boolean userChrShown = false;
        long chrEntries = 0;
        for (String line : output.split("\\R")) {
            String trimmed = line.startsWith("> ") ? line.substring(2) : line;
            boolean isChrEntry = trimmed.startsWith("[로컬] chr = ") || trimmed.startsWith("[전역] chr = ");
            if (isChrEntry) {
                chrEntries++;
            }
            if (trimmed.startsWith("[로컬] chr = 99") || trimmed.startsWith("[전역] chr = 99")) {
                userChrShown = true;
            }
        }
        assertTrue(userChrShown,
                () -> "사용자 전역 chr(=99)가 inspect에 보여야 한다:\n" + output);

        // 네이티브 chr(<native fn>)는 별도 항목으로 노출되면 안 된다 — 가려졌고 builtin 경계도 막는다.
        // chr 항목이 정확히 하나(사용자 값)만 나타나야 한다.
        final long count = chrEntries;
        assertEquals(1, count,
                () -> "chr는 사용자 전역 한 항목만 inspect에 나타나야 한다(네이티브 미노출), got " + count + ":\n" + output);

        // 사용자 코드에서 chr를 출력하면 네이티브가 아니라 사용자 값 99가 나와야 한다(셰도잉).
        RunResult result = new CodeFab().run("var chr = 99;\nprint chr;\n");
        assertTrue(result.success(),
                () -> "셰도잉 소스는 성공해야 한다, diagnostics: " + result.diagnostics());
        assertEquals(List.of("99"), result.output());
    }
}
