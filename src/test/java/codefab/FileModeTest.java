package codefab;

import codefab.shell.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * File mode integration tests (contract §6). Drives {@link Main#dispatch} with a
 * scripted (empty) command stream and asserts on the captured stdout/stderr and
 * the returned exit code — never on {@code System.out}, so tests are isolated.
 */
class FileModeTest {

    /** Result of one dispatch: exit code plus captured out/err. */
    private record Captured(int code, String out, String err) {}

    private Captured dispatch(String[] args) {
        BufferedReader in = new BufferedReader(new StringReader(""));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        int code = Main.dispatch(args, in, out, err);
        return new Captured(code,
                outBytes.toString(StandardCharsets.UTF_8),
                errBytes.toString(StandardCharsets.UTF_8));
    }

    private Path writeScript(Path dir, String source) throws Exception {
        Path file = dir.resolve("script.txt");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void runSubcommandRunsFileAndPrintsResult(@TempDir Path dir) throws Exception {
        Path file = writeScript(dir, "print 1+2;\n");
        Captured r = dispatch(new String[]{"run", file.toString()});
        assertEquals(0, r.code(), () -> "stderr: " + r.err());
        assertTrue(r.out().contains("3"), () -> "expected 3 in stdout:\n" + r.out());
    }

    @Test
    void bareFilePathIsBackwardCompatibleWithRun(@TempDir Path dir) throws Exception {
        Path file = writeScript(dir, "print 1+2;\n");
        Captured r = dispatch(new String[]{file.toString()});
        assertEquals(0, r.code(), () -> "stderr: " + r.err());
        assertTrue(r.out().contains("3"), () -> "expected 3 in stdout:\n" + r.out());
    }

    @Test
    void missingFileExitsWithNoInputAndReportsPath(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist.txt");
        Captured r = dispatch(new String[]{"run", missing.toString()});
        assertEquals(66, r.code());
        assertTrue(r.err().contains("Could not read file '"),
                () -> "expected missing-file message in stderr:\n" + r.err());
    }

    @Test
    void runtimeErrorExitsWithDataErrorAndIncludesLineNumber(@TempDir Path dir) throws Exception {
        // Line 1 is fine; the fault is on line 2, so the diagnostic must cite 2.
        Path file = writeScript(dir, "var a = 1;\nprint 1/0;\n");
        Captured r = dispatch(new String[]{"run", file.toString()});
        assertEquals(65, r.code());
        assertTrue(r.err().contains("Division by zero."),
                () -> "expected division-by-zero in stderr:\n" + r.err());
        assertTrue(r.err().contains("2"),
                () -> "expected line number 2 in stderr:\n" + r.err());
        // The runtime error happens at print time; nothing should have been printed.
        assertFalse(r.out().contains("3"), () -> "unexpected output:\n" + r.out());
    }
}
