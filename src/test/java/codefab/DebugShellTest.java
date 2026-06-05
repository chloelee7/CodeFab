package codefab;

import codefab.shell.DebugShell;
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
 * Characterization tests for the debug shell. They pin the observable behaviour
 * (output strings) so the Command-pattern refactor of command dispatch stays
 * behaviour-preserving.
 */
class DebugShellTest {

    @TempDir
    Path dir;

    private String drive(String program, String commands) throws Exception {
        Path file = dir.resolve("dbg.cf");
        Files.writeString(file, program);
        BufferedReader in = new BufferedReader(new StringReader(commands));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new DebugShell(in, out, file.toString()).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void stepExecutesStatementsInOrder() throws Exception {
        String out = drive("print 1;\nprint 2;\n", "step\nstep\nexit\n");
        assertTrue(out.contains("[DEBUG] 소스코드 로딩:"), () -> out);
        assertTrue(out.contains("1"), () -> out);
        assertTrue(out.contains("2"), () -> out);
        assertTrue(out.contains("[DEBUG] 실행 완료."), () -> out);
    }

    @Test
    void breakpointStopsAtLine() throws Exception {
        String out = drive("var a = 1;\nvar b = 2;\nvar c = 3;\n", "break 2\ncontinue\nexit\n");
        assertTrue(out.contains("2번째 줄에 breakpoint 설정"), () -> out);
        assertTrue(out.contains("정지 (breakpoint)"), () -> out);
    }

    @Test
    void watchPrintsVariableValueAfterStep() throws Exception {
        String out = drive("var x = 5;\nvar y = 9;\n", "watch x\nstep\nexit\n");
        assertTrue(out.contains("'x' 감시 등록"), () -> out);
        assertTrue(out.contains("[WATCH] x = 5"), () -> out);
    }

    @Test
    void inspectDumpsScopeVariables() throws Exception {
        String out = drive("var a = 1;\nvar b = 2;\n", "step\ninspect\nexit\n");
        assertTrue(out.contains("현재 스코프 변수"), () -> out);
        assertTrue(out.contains("a = 1"), () -> out);
    }

    @Test
    void unknownCommandReported() throws Exception {
        String out = drive("print 1;\n", "frobnicate\nexit\n");
        assertTrue(out.contains("Unknown command: frobnicate"), () -> out);
    }

    @Test
    void breakpointsAndRemoveManageList() throws Exception {
        String out = drive("var a = 1;\nvar b = 2;\n",
                "break 1\nbreakpoints\nremove 1\nbreakpoints\nexit\n");
        assertTrue(out.contains("1번째 줄에 breakpoint 설정"), () -> out);
        assertTrue(out.contains("Breakpoints:"), () -> out);
        assertTrue(out.contains("1번째 줄 breakpoint 해제"), () -> out);
        assertTrue(out.contains("설정된 breakpoint 없음"), () -> out);
    }
}
