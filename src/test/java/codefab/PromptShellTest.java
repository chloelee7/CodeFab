package codefab;

import codefab.shell.PromptShell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PromptShellTest {

    private String drive(String input) {
        BufferedReader reader = new BufferedReader(new StringReader(input));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new PromptShell(reader, out).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("간단한 프로그램을 실행하고 변수를 유지한다")
    void runsSimpleProgramAndKeepsVariables() {
        String output = drive("var a = 5;\nvar b = 10;\nprint a + b;\n:exit\n");
        assertTrue(output.contains("15"), () -> "expected 15 in output:\n" + output);
    }

    @Test
    @DisplayName("닫는 중괄호까지 여러 줄 블록을 누적한다")
    void accumulatesMultiLineBlockUntilBraceCloses() {
        String program = "var total = 0;\n{\n  total = total + 1;\n  total = total + 2;\n}\nprint total;\n:exit\n";
        String output = drive(program);
        assertTrue(output.contains("3"), () -> "expected 3 in output:\n" + output);
    }

    @Test
    @DisplayName("에러 진단을 출력하고 세션을 유지한다")
    void reportsDiagnosticsWithoutCrashing() {
        String output = drive("print missing;\n:exit\n");
        assertTrue(output.contains("Undefined variable 'missing'."), () -> output);
    }

    @Test
    @DisplayName("괄호 균형 기반으로 입력 완성 여부를 판단한다")
    void completenessHeuristicHandlesBalancedInput() {
        assertFalse(PromptShell.isComplete("var a = (1 + 2"));   // open paren
        assertFalse(PromptShell.isComplete("{ var a = 1;"));     // open brace
        assertFalse(PromptShell.isComplete("print 1"));          // no terminator
        assertTrue(PromptShell.isComplete("print 1;"));
        assertTrue(PromptShell.isComplete("{ var a = 1; }"));
        assertFalse(PromptShell.isComplete("print \"a;"));       // unterminated string
    }
}
