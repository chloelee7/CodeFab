package codefab;

import codefab.shell.DebugShell;
import org.junit.jupiter.api.DisplayName;
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

@DisplayName("디버그 셸")
class DebugShellTest {

    @TempDir
    Path dir;

    private String drive(String program, String commands) throws Exception {
        Path file = dir.resolve("dbg.cf");
        Files.writeString(file, program);
        BufferedReader in = new BufferedReader(new StringReader(commands));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        new DebugShell(in, out, err, file.toString()).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("파일 없음 오류는 stdout이 아닌 stderr로 출력된다")
    void missingFileErrorGoesToStderrNotStdout() throws Exception {
        BufferedReader in = new BufferedReader(new StringReader(""));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        new DebugShell(in, out, err, "/no/such/file.cf").run();
        String outStr = outBytes.toString(StandardCharsets.UTF_8);
        String errStr = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(errStr.contains("Error: file not found:"), () -> "expected on err: " + errStr);
        assertFalse(outStr.contains("Error: file not found:"), () -> "must not be on out: " + outStr);
    }

    @Test
    @DisplayName("step은 구문을 순서대로 실행한다")
    void stepExecutesStatementsInOrder() throws Exception {
        String out = drive("print 1;\nprint 2;\n", "step\nstep\nexit\n");
        assertTrue(out.contains("[DEBUG] 소스코드 로딩:"), () -> out);
        assertTrue(out.contains("1"), () -> out);
        assertTrue(out.contains("2"), () -> out);
        assertTrue(out.contains("[DEBUG] 실행 완료."), () -> out);
    }

    @Test
    @DisplayName("breakpoint를 설정한 줄에서 정지한다")
    void breakpointStopsAtLine() throws Exception {
        String out = drive("var a = 1;\nvar b = 2;\nvar c = 3;\n", "break 2\ncontinue\nexit\n");
        assertTrue(out.contains("2번째 줄에 breakpoint 설정"), () -> out);
        assertTrue(out.contains("정지 (breakpoint)"), () -> out);
    }

    @Test
    @DisplayName("watch한 변수 값은 step 이후 출력된다")
    void watchPrintsVariableValueAfterStep() throws Exception {
        String out = drive("var x = 5;\nvar y = 9;\n", "watch x\nstep\nexit\n");
        assertTrue(out.contains("'x' 감시 등록"), () -> out);
        assertTrue(out.contains("[WATCH] x = 5"), () -> out);
    }

    @Test
    @DisplayName("inspect는 현재 스코프 변수를 덤프한다")
    void inspectDumpsScopeVariables() throws Exception {
        String out = drive("var a = 1;\nvar b = 2;\n", "step\ninspect\nexit\n");
        assertTrue(out.contains("현재 스코프 변수"), () -> out);
        assertTrue(out.contains("a = 1"), () -> out);
    }

    @Test
    @DisplayName("알 수 없는 명령은 오류 메시지를 출력한다")
    void unknownCommandReported() throws Exception {
        String out = drive("print 1;\n", "frobnicate\nexit\n");
        assertTrue(out.contains("Unknown command: frobnicate"), () -> out);
    }

    @Test
    @DisplayName("인자 없는 watch는 거부되고 빈 변수명이 등록되지 않는다")
    void watchWithoutArgIsRejectedNotRegistered() throws Exception {
        String out = drive("var x = 1;\n", "watch\nwatches\nexit\n");
        assertTrue(out.contains("Usage: watch <variable>"), () -> out);
        assertTrue(out.contains("감시 중인 변수 없음"), () -> out);
        assertFalse(out.contains("'' 감시 등록"), () -> out);
    }

    @Test
    @DisplayName("breakpoints/remove로 목록을 관리한다")
    void breakpointsAndRemoveManageList() throws Exception {
        String out = drive("var a = 1;\nvar b = 2;\n",
                "break 1\nbreakpoints\nremove 1\nbreakpoints\nexit\n");
        assertTrue(out.contains("1번째 줄에 breakpoint 설정"), () -> out);
        assertTrue(out.contains("Breakpoints:"), () -> out);
        assertTrue(out.contains("1번째 줄 breakpoint 해제"), () -> out);
        assertTrue(out.contains("설정된 breakpoint 없음"), () -> out);
    }
}
