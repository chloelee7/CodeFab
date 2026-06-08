package codefab.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codefab.RunResult;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CompareMode")
class CompareModeTest {

    @Test
    @DisplayName("출력이 다르면 양쪽 출력을 보여주고 코드 65를 반환한다")
    void reportsDifferentOutputs(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("compare-output-diff.cfab");
        Files.writeString(file, "print 1;\n");
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        CompareMode mode = new CompareMode(
            file.toString(),
            source -> new RunResult(true, List.of("java"), List.of()),
            source -> new RunResult(true, List.of("selfhost"), List.of()));

        int code = mode.execute(new BufferedReader(new StringReader("")), out, err);

        String stdout = outBytes.toString(StandardCharsets.UTF_8);
        String stderr = errBytes.toString(StandardCharsets.UTF_8);
        assertEquals(65, code);
        assertEquals("", stderr);
        assertTrue(stdout.contains("Success: identical"), () -> stdout);
        assertTrue(stdout.contains("Output: different"), () -> stdout);
        assertTrue(stdout.contains("Diagnostics: identical"), () -> stdout);
        assertTrue(stdout.contains("Java output:\njava"), () -> stdout);
        assertTrue(stdout.contains("Selfhost output:\nselfhost"), () -> stdout);
    }
}
