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
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("디버그 셸")
class DebugShellTest {

    @TempDir
    Path tempDir;

    private String drive(String source, String commands) throws IOException {
        return DebugShellTestSupport.drive(tempDir, source, commands);
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

    @Test
    @DisplayName("continue는 함수 본문 내부 breakpoint에서 멈춘다")
    void continueStopsAtBreakpointInsideFunctionBody() throws IOException {
        String source = String.join("\n",
                "Func f() {",
                "  print 99;",
                "}",
                "f();",
                "");

        String output = drive(source, "break 2\ncontinue\nexit\n");

        assertTrue(output.contains("2번째 줄에서 정지 (breakpoint)"), () -> output);
        assertFalse(containsOutputLine(output, "99"), () -> output);
    }

    @Test
    @DisplayName("continue는 for 본문 내부 breakpoint에서 멈춘다")
    void continueStopsAtBreakpointInsideForBody() throws IOException {
        String source = String.join("\n",
                "for (var i = 0; i < 3; i = i + 1) {",
                "  print i;",
                "}",
                "");

        String output = drive(source, "break 2\ncontinue\nexit\n");

        assertTrue(output.contains("2번째 줄에서 정지 (breakpoint)"), () -> output);
        assertFalse(containsOutputLine(output, "0"), () -> output);
    }

    @Test
    @DisplayName("step은 함수 본문 내부로 진입한다")
    void stepDescendsIntoFunctionBody() throws IOException {
        String source = String.join("\n",
                "Func f() {",
                "  print 99;",
                "}",
                "f();",
                "");

        String output = drive(source, "step\nstep\nstep\nexit\n");

        assertTrue(output.contains("2번째 줄에서 정지"), () -> output);
    }

    @Test
    @DisplayName("next는 함수 호출을 step-over한다 (본문 내부로 진입하지 않음)")
    void nextStepsOverFunctionCall() throws IOException {
        String source = String.join("\n",
                "Func f() {",
                "  print 99;",
                "}",
                "f();",
                "print 1;",
                "");

        // 1줄(Func 정의)에서 정지 → next로 정의 실행 → 4줄(f();)에서 정지 →
        // next로 f() 호출을 step-over(본문 진입 X, 99 출력) → 5줄(print 1)에서 정지
        String output = drive(source, "next\nnext\nnext\nexit\n");

        assertTrue(output.contains("4번째 줄에서 정지"), () -> output);
        assertTrue(output.contains("5번째 줄에서 정지"), () -> output);
        assertTrue(containsOutputLine(output, "99"), () -> output);   // 본문은 실행됨
        assertFalse(output.contains("2번째 줄에서 정지"), () -> output); // 본문 내부엔 정지 안 함
    }

    @Test
    @DisplayName("step은 함수 호출 시 본문 내부로 진입한다 (step-into)")
    void stepStepsIntoFunctionCall() throws IOException {
        String source = String.join("\n",
                "Func f() {",
                "  print 99;",
                "}",
                "f();",
                "print 1;",
                "");

        // 1줄 정지 → step(정의) → 4줄 f() 정지 → step → 본문 2줄 print 99에서 정지
        String output = drive(source, "step\nstep\nstep\nexit\n");

        assertTrue(output.contains("2번째 줄에서 정지"), () -> output); // 본문 진입
    }

    @Test
    @DisplayName("함수 본문 내부에서 멈춰 파라미터·지역변수를 inspect로 확인한다")
    void inspectShowsFunctionLocalsWhenStoppedInsideBody() throws IOException {
        String source = String.join("\n",
                "Func f(a) {",
                "  var b = a + 1;",
                "  print b;",
                "}",
                "f(10);",
                "");

        String output = drive(source, "break 3\ncontinue\ninspect\nexit\n");

        assertTrue(output.contains("3번째 줄에서 정지 (breakpoint)"), () -> output);
        assertTrue(output.contains("a = 10"), () -> output);
        assertTrue(output.contains("b = 11"), () -> output);
    }

    @Test
    @DisplayName("continue는 while 본문 내부 breakpoint에서 멈춘다")
    void continueStopsAtBreakpointInsideWhileBody() throws IOException {
        String source = String.join("\n",
                "var x = 0;",
                "while (x < 2) {",
                "  x = x + 1;",
                "}",
                "print x;",
                "");

        String output = drive(source, "break 3\ncontinue\nexit\n");

        assertTrue(output.contains("3번째 줄에서 정지 (breakpoint)"), () -> output);
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
        assertTrue(errStr.contains("Error: cannot read file:"), () -> "expected on err: " + errStr);
        assertFalse(outStr.contains("Error: cannot read file:"), () -> "must not be on out: " + outStr);
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
