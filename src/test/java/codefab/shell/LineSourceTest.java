package codefab.shell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 스펙 07 — JLine 라인 편집 통합. 통합 로직(추상화·분기·폴백·EOF·prompt 출력)을 고정한다.
 *
 * <p>JLine LineReader의 실제 라인 편집(히스토리/wide-char 재그리기)은 PTY 없이 단위 테스트가
 * 불가하므로 테스트하지 않는다. 테스트 환경은 {@code System.console()==null}이라고 가정한다 —
 * 어떤 단언도 실제 터미널/JLine 경로를 활성화하면 안 된다.
 *
 * <p>아직 미구현인 신규 타입({@code LineSource}, {@code BufferedLineSource},
 * {@code LineSources})에 대한 red 테스트다. 구현은 shell-integrator가 한다.
 */
@DisplayName("LineSource 추상화 (JLine 통합)")
class LineSourceTest {

    private static PrintStream utf8(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private static BufferedReader reader(String input) {
        return new BufferedReader(new StringReader(input));
    }

    // 스펙 TDD #1: BufferedLineSource.readLine —
    // prompt를 out에 그대로 쓰고, 다음 한 줄을 반환한다.
    @Test
    @DisplayName("BufferedLineSource.readLine은 prompt를 out에 쓰고 다음 한 줄을 반환한다")
    void bufferedLineSourceWritesPromptAndReturnsLine() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = utf8(bytes);
        LineSource source = new BufferedLineSource(reader("first\nsecond\n"), out);

        String first = source.readLine("> ");
        assertEquals("first", first, "첫 줄을 반환해야 한다");
        assertEquals("> ", bytes.toString(StandardCharsets.UTF_8),
                "prompt가 out에 그대로 기록되어야 한다");

        String second = source.readLine("....... > ");
        assertEquals("second", second, "두 번째 줄을 반환해야 한다");
        assertEquals("> ....... > ", bytes.toString(StandardCharsets.UTF_8),
                "각 호출의 prompt가 순서대로 누적 기록되어야 한다");
    }

    // 스펙 TDD #1: BufferedLineSource.readLine —
    // 입력이 끝나면(EOF) prompt는 여전히 쓰되 null을 반환한다.
    @Test
    @DisplayName("BufferedLineSource.readLine은 EOF에서 null을 반환한다")
    void bufferedLineSourceReturnsNullAtEof() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = utf8(bytes);
        LineSource source = new BufferedLineSource(reader(""), out);

        assertNull(source.readLine("> "), "EOF면 null을 반환해야 한다");
        assertEquals("> ", bytes.toString(StandardCharsets.UTF_8),
                "EOF여도 prompt는 먼저 출력되어야 한다(기존 readLine 경로와 동일)");
    }

    // 스펙 설계 §"BufferedLineSource" — close()는 무해(no-op 수준).
    @Test
    @DisplayName("BufferedLineSource는 AutoCloseable이며 close가 예외 없이 동작한다")
    void bufferedLineSourceIsCloseable() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (LineSource source = new BufferedLineSource(reader("x\n"), utf8(bytes))) {
            assertEquals("x", source.readLine("> "));
        }
    }

    // 스펙 TDD #2: LineSources.forInteractive —
    // 테스트 환경(System.console()==null)에서는 반드시 BufferedLineSource를 반환한다.
    // 이 단언이 곧 "테스트가 JLine 경로로 절대 새지 않음"을 보장한다.
    @Test
    @DisplayName("forInteractive는 비대화형(console==null) 환경에서 BufferedLineSource로 폴백한다")
    void forInteractiveFallsBackToBufferedWhenNoConsole() {
        // 전제: 테스트 환경은 콘솔이 없다. 이 가정이 깨지면(=실제 TTY) 테스트가 무의미하므로
        // 폴백 단언 전에 명시적으로 전제를 고정한다.
        assertNull(System.console(), "테스트 환경에는 콘솔이 없어야 한다(JLine 비활성)");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        LineSource source = LineSources.forInteractive(reader("hello\n"), utf8(bytes));

        assertInstanceOf(BufferedLineSource.class, source,
                "비대화형에서는 JLine이 아니라 BufferedLineSource여야 한다");
    }

    // 스펙 §"핵심 불변식": 폴백된 BufferedLineSource는 기존 readLine 경로와 1:1 동일하게
    // 동작한다 — prompt를 쓰고 입력 라인을 그대로 반환한다.
    @Test
    @DisplayName("forInteractive 폴백 결과는 prompt 출력 + 라인 반환이 기존과 동일하다")
    void forInteractiveFallbackBehavesLikeBufferedReader() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        LineSource source = LineSources.forInteractive(reader("ping\n"), utf8(bytes));

        assertEquals("ping", source.readLine("> "));
        assertEquals("> ", bytes.toString(StandardCharsets.UTF_8));
        assertNull(source.readLine("> "), "두 번째 호출은 EOF로 null");
    }
}
