package codefab.shell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 스펙 07 TDD #3 (회귀 보강).
 *
 * <p>기존 PromptShellTest/DebugShellTest는 {@code contains}로 결과값만 검사하므로, prompt 문자열·
 * 출력 순서가 LineSource 도입 과정에서 미묘하게 깨져도(예: prompt 누락/중복, flush 순서 어긋남)
 * 잡지 못할 수 있다. 이 테스트는 BufferedReader 생성자로 셸을 구성했을 때
 * <b>prompt를 포함한 출력 시퀀스가 기존과 정확히 동일</b>함을 고정한다.
 *
 * <p>셸 내부가 {@code out.print(prompt); in.readLine()} 직접 호출에서
 * {@code lineSource.readLine(prompt)} 위임으로 바뀌어도(스펙 §"셸 수정") 비대화형 경로의
 * 바이트 출력은 1:1 동일해야 한다(스펙 §"핵심 불변식"). 기존 BufferedReader 생성자는 무변경 유지.
 */
@DisplayName("LineSource 도입 후 BufferedReader 경로 출력 회귀")
class ShellLineSourceRegressionTest {

    @TempDir
    Path tempDir;

    private static PrintStream utf8(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    // PromptShell: PRIMARY/CONTINUATION prompt와 배너, 출력 라인의 정확한 시퀀스를 고정.
    // 입력: 멀티라인 블록 한 개 + :exit.
    @Test
    @DisplayName("PromptShell(BufferedReader)은 배너·프롬프트·출력 시퀀스를 기존과 동일하게 낸다")
    void promptShellBufferedReaderOutputIsByteStable() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedReader in = new BufferedReader(new StringReader(
                "var total = 0;\n{\n  total = total + 5;\n}\nprint total;\n:exit\n"));

        new PromptShell(in, utf8(bytes)).run();

        String output = bytes.toString(StandardCharsets.UTF_8);
        // 배너 1회.
        assertTrue(output.startsWith("CodeFab REPL. Type 'exit' to quit." + System.lineSeparator()),
                () -> "배너가 맨 앞에 그대로 나와야 한다:\n" + output);
        // PRIMARY prompt와 CONTINUATION prompt가 모두 등장한다(멀티라인 블록이므로).
        assertTrue(output.contains("> "), () -> "PRIMARY prompt 누락:\n" + output);
        assertTrue(output.contains("....... > "), () -> "CONTINUATION prompt 누락:\n" + output);
        // 블록 누적 후 실행 결과.
        assertTrue(output.contains("5"), () -> "블록 실행 결과 5 누락:\n" + output);
    }

    // 단일 라인 입력에서 정확한 prompt 개수를 고정 — prompt 중복/누락 회귀를 잡는다.
    // 입력 줄 2개(프로그램 1줄 + :exit) → readLine 호출 2회 직전마다 PRIMARY prompt 1회씩.
    @Test
    @DisplayName("PromptShell은 readLine 호출마다 prompt를 정확히 한 번 출력한다(중복/누락 없음)")
    void promptShellEmitsExactlyOnePromptPerReadLine() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedReader in = new BufferedReader(new StringReader("print 7;\n:exit\n"));

        new PromptShell(in, utf8(bytes)).run();

        String output = bytes.toString(StandardCharsets.UTF_8);
        int primaryCount = countOccurrences(output, "> ");
        // "print 7;" 줄과 ":exit" 줄 직전에 각각 PRIMARY prompt 1회 = 2회.
        // (CONTINUATION "....... > "도 "> "를 포함하지만 이 입력엔 멀티라인이 없어 미발생.)
        assertEquals(2, primaryCount, () -> "PRIMARY prompt는 readLine마다 정확히 1회여야 한다:\n" + output);
        assertTrue(output.contains("7"), () -> "출력 7 누락:\n" + output);
    }

    // DebugShell: 디버그 진입/정지/완료 메시지와 명령 프롬프트("> ")가 BufferedReader 경로에서
    // 그대로 유지됨을 고정(§10 디버그 관찰 계약 불변).
    @Test
    @DisplayName("DebugShell(BufferedReader)은 디버그 배너·정지·프롬프트·완료 메시지를 그대로 낸다")
    void debugShellBufferedReaderOutputIsStable() throws IOException {
        Path script = tempDir.resolve("dbg.cfab");
        Files.writeString(script, "print 1;\nprint 2;\n", StandardCharsets.UTF_8);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedReader in = new BufferedReader(new StringReader("step\nstep\nexit\n"));
        PrintStream err = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);

        new DebugShell(in, utf8(bytes), err, script.toString()).run();

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[DEBUG] 소스코드 로딩:"), () -> "로딩 배너 누락:\n" + output);
        assertTrue(output.contains("번째 줄에서 정지"), () -> "정지 메시지 누락:\n" + output);
        assertTrue(output.contains("> "), () -> "디버그 명령 프롬프트 누락:\n" + output);
        assertTrue(output.contains("[DEBUG] 실행 완료."), () -> "완료 메시지 누락:\n" + output);
        assertTrue(output.contains("1"), () -> "출력 1 누락:\n" + output);
        assertTrue(output.contains("2"), () -> "출력 2 누락:\n" + output);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
