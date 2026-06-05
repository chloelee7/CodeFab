package codefab;

import codefab.shell.Debugger;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug mode integration tests (contract §10). The debugger is driven with a
 * scripted command stream injected through a {@link BufferedReader}; output is
 * captured via a {@link ByteArrayOutputStream}. EOF on the command stream is
 * treated as "resume", so each script ends naturally once enough step/continue
 * commands have driven execution to completion.
 */
class DebugModeTest {

    private static final String SRC =
            "var a = 3;\n" +
            "var b = a + 1;\n" +
            "print a;\n" +
            "print b;\n";

    private String debug(String source, String commands) {
        BufferedReader in = new BufferedReader(new StringReader(commands));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new Debugger("/tmp/factory.txt", source, in, out).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printsLoadBannerAndPausesAtFirstStatement() {
        String out = debug(SRC, "step\nstep\nstep\nstep\nstep\n");
        assertTrue(out.contains("[DEBUG] 소스코드 로딩:"),
                () -> "expected load banner:\n" + out);
        assertTrue(out.contains("1번째 줄에서 정지"),
                () -> "expected stop at line 1:\n" + out);
        assertTrue(out.contains("→ var a = 3;"),
                () -> "expected source text of line 1:\n" + out);
    }

    @Test
    void stepAdvancesStatementByStatement() {
        String out = debug(SRC, "step\nstep\nstep\nstep\nstep\n");
        assertTrue(out.contains("1번째 줄에서 정지"), () -> out);
        assertTrue(out.contains("2번째 줄에서 정지"), () -> out);
        assertTrue(out.contains("3번째 줄에서 정지"), () -> out);
        assertTrue(out.contains("4번째 줄에서 정지"), () -> out);
    }

    @Test
    void breakAndContinueStopAtBreakpoint() {
        String out = debug(SRC, "break 3\ncontinue\nstep\nstep\n");
        assertTrue(out.contains("3번째 줄에 breakpoint 설정"),
                () -> "expected breakpoint-set message:\n" + out);
        assertTrue(out.contains("3번째 줄에서 정지 (breakpoint)"),
                () -> "expected breakpoint stop:\n" + out);
        // continue must skip line 2 (no breakpoint there) before reaching line 3.
        assertFalse(out.contains("2번째 줄에서 정지"),
                () -> "continue should not have paused at line 2:\n" + out);
    }

    @Test
    void watchRegistersAndPrintsValueAtEachStop() {
        String out = debug(SRC, "watch a\nstep\nstep\nstep\nstep\n");
        assertTrue(out.contains("[WATCH] 'a' 감시 등록"),
                () -> "expected watch registration:\n" + out);
        assertTrue(out.contains("[WATCH] a = "),
                () -> "expected watched value at a stop:\n" + out);
        // After line 1 executes, a is bound to 3.
        assertTrue(out.contains("[WATCH] a = 3"),
                () -> "expected watched value a = 3:\n" + out);
    }

    @Test
    void inspectShowsVariableValueAndType() {
        // After the first statement runs, `a` is defined; inspect at line 2.
        String out = debug(SRC, "step\ninspect\nstep\nstep\nstep\n");
        assertTrue(out.contains("a = 3"),
                () -> "expected a = 3 from inspect:\n" + out);
        assertTrue(out.contains("(Number)"),
                () -> "expected Number type from inspect:\n" + out);
    }

    @Test
    void nextStepsOverBlockWithoutEnteringIt() {
        // `next` at the block statement (line 2) must resume past the block body
        // (line 3) and re-pause at the same level — line 5 (print x).
        String blockSrc =
                "var x = 1;\n" +
                "{\n" +
                "  var y = 2;\n" +
                "}\n" +
                "print x;\n";
        String out = debug(blockSrc, "next\nnext\nnext\nnext\n");
        assertTrue(out.contains("5번째 줄에서 정지"),
                () -> "expected to step over block to line 5:\n" + out);
        // The block body (line 3) lives at a deeper depth; next must not pause there.
        assertFalse(out.contains("3번째 줄에서 정지"),
                () -> "next should not have descended into the block (line 3):\n" + out);
    }
}
