package codefab;

import codefab.shell.DebugShell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugShellTest {

    @TempDir
    Path tempDir;

    private String drive(String source, String commands) throws IOException {
        Path script = tempDir.resolve("program.cfab");
        Files.writeString(script, source, StandardCharsets.UTF_8);

        BufferedReader reader = new BufferedReader(new StringReader(commands));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new DebugShell(reader, out, script.toString()).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private boolean containsOutputLine(String output, String expected) {
        for (String line : output.split("\\R")) {
            if (line.equals(expected) || line.equals("> " + expected)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("continue는 중첩 블록 내부 breakpoint에서 실행 전 멈춘다")
    void continueStopsBeforeNestedBlockBreakpoint() throws IOException {
        String source = String.join("\n",
                "var x = 0;",
                "{",
                "  x = 1;",
                "  print x;",
                "}",
                "print 2;",
                "");

        String output = drive(source, "break 4\ncontinue\nexit\n");

        assertTrue(output.contains("4번째 줄에서 정지"), () -> output);
        assertFalse(containsOutputLine(output, "1"), () -> output);
        assertFalse(containsOutputLine(output, "2"), () -> output);
    }

    @Test
    @DisplayName("breakpoint에서 멈춘 뒤 step은 해당 문장을 건너뛰지 않는다")
    void stepAfterBreakpointExecutesStoppedStatement() throws IOException {
        String source = String.join("\n",
                "var x = 1;",
                "print x;",
                "print 2;",
                "");

        String output = drive(source, "break 2\ncontinue\nstep\nexit\n");

        assertTrue(output.contains("2번째 줄에서 정지"), () -> output);
        assertTrue(containsOutputLine(output, "1"), () -> output);
        assertFalse(containsOutputLine(output, "2"), () -> output);
    }

    @Test
    @DisplayName("continue는 실행되지 않는 if 분기 내부 breakpoint에서 멈추지 않는다")
    void continueDoesNotStopAtBreakpointInsideUntakenIfBranch() throws IOException {
        String source = String.join("\n",
                "var x = 1;",
                "if (false) {",
                "  print x;",
                "}",
                "print 2;",
                "");

        String output = drive(source, "break 3\ncontinue\n");

        assertFalse(output.contains("3번째 줄에서 정지 (breakpoint)"), () -> output);
        assertFalse(containsOutputLine(output, "1"), () -> output);
        assertTrue(containsOutputLine(output, "2"), () -> output);
    }

    @Test
    @DisplayName("continue는 실행되지 않는 while 본문 내부 breakpoint에서 멈추지 않는다")
    void continueDoesNotStopAtBreakpointInsideSkippedWhileBody() throws IOException {
        String source = String.join("\n",
                "var x = 1;",
                "while (false) {",
                "  print x;",
                "}",
                "print 2;",
                "");

        String output = drive(source, "break 3\ncontinue\n");

        assertFalse(output.contains("3번째 줄에서 정지 (breakpoint)"), () -> output);
        assertFalse(containsOutputLine(output, "1"), () -> output);
        assertTrue(containsOutputLine(output, "2"), () -> output);
    }
}
