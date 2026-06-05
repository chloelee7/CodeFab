package codefab;

import codefab.shell.PromptShell;
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
    void runsSimpleProgramAndKeepsVariables() {
        String output = drive("var a = 5;\nvar b = 10;\nprint a + b;\n:exit\n");
        assertTrue(output.contains("15"), () -> "expected 15 in output:\n" + output);
    }

    @Test
    void accumulatesMultiLineBlockUntilBraceCloses() {
        // Refined: a bare `{}` block runs immediately when its brace closes —
        // no blank line required (a bare block cannot grow an `else`/etc., so it
        // is complete on `}`). The trailing `print total;` comes right after the
        // block with no blank line, and `3` is printed without any flush trigger.
        String program = "var total = 0;\n{\n  total = total + 1;\n  total = total + 2;\n}\nprint total;\n:exit\n";
        String output = drive(program);
        assertTrue(output.contains("3"), () -> "expected 3 in output:\n" + output);
    }

    @Test
    void ifBlockDoesNotRunUntilBlankLine() {
        // The if-block closes its brace but, with no blank line following,
        // it must NOT execute yet. The trailing `print 999;` is accumulated
        // into the same (still-pending) buffer rather than run on its own.
        // EOF here is reached while the buffer holds an incomplete combined
        // input, so neither the if body (1) nor 999 should be printed.
        String program = "if (true) { print 1; }\nprint 999;\n";
        String output = drive(program);
        assertFalse(output.contains("1"),
                () -> "if body must not run without a trailing blank line:\n" + output);
        assertFalse(output.contains("999"),
                () -> "trailing statement must be accumulated, not run, while block is pending:\n" + output);
    }

    @Test
    void ifBlockRunsAfterBlankLine() {
        String program = "if (true) { print 42; }\n\n:exit\n";
        String output = drive(program);
        assertTrue(output.contains("42"),
                () -> "if body must run once a blank line follows the block:\n" + output);
    }

    @Test
    void ifElseRunsOnSingleEnter() {
        // Refined: an if/else that closes its final `}` is complete (no further
        // `else` can attach), so it runs on a single Enter with NO blank line.
        // We `:exit` immediately after the block-closing line; the else branch
        // (false condition) must still have executed and printed 2.
        String program = "if (false) { print 1; } else { print 2; }\n:exit\n";
        String output = drive(program);
        assertTrue(output.contains("2"),
                () -> "else branch must run on a single Enter, no blank line needed:\n" + output);
        assertFalse(output.contains("1"),
                () -> "then branch must not run for a false condition:\n" + output);
    }

    @Test
    void whileBlockRunsOnSingleEnter() {
        // Refined: a while block is complete on its closing `}` and runs on a
        // single Enter. We `:exit` right after the block line with no blank line;
        // the loop body must already have run, printing 0,1,2.
        String program = "var i = 0; while (i < 3) { print i; i = i + 1; }\n:exit\n";
        String output = drive(program);
        assertTrue(output.contains("0") && output.contains("1") && output.contains("2"),
                () -> "while body must run on a single Enter, no blank line needed:\n" + output);
    }

    @Test
    void forBlockRunsOnSingleEnter() {
        // Refined: a for block is complete on its closing `}` and runs on a
        // single Enter. `:exit` follows immediately with no blank line; the loop
        // body must already have run, printing 0,1,2.
        String program = "for (var j = 0; j < 3; j = j + 1) { print j; }\n:exit\n";
        String output = drive(program);
        assertTrue(output.contains("0") && output.contains("1") && output.contains("2"),
                () -> "for body must run on a single Enter, no blank line needed:\n" + output);
    }

    @Test
    void simpleStatementStillRunsImmediately() {
        // Regression: simple statements (no `{` block) still run on Enter,
        // with no blank line required.
        String program = "var a = 5; var b = 10; print a + b;\n:exit\n";
        String output = drive(program);
        assertTrue(output.contains("15"),
                () -> "simple statement must run immediately without a blank line:\n" + output);
    }

    @Test
    void reportsDiagnosticsWithoutCrashing() {
        String output = drive("print missing;\n:exit\n");
        assertTrue(output.contains("Undefined variable 'missing'."), () -> output);
    }

    @Test
    void bareExitCommandTerminatesSession() {
        // PDF prompt-mode spec: bare `exit`/`quit` end the session.
        String output = drive("var a = 1;\nexit\nprint 999;\n");
        assertFalse(output.contains("999"),
                () -> "input after bare 'exit' must not run:\n" + output);
    }

    @Test
    void bareQuitCommandTerminatesSession() {
        String output = drive("quit\nprint 999;\n");
        assertFalse(output.contains("999"),
                () -> "input after bare 'quit' must not run:\n" + output);
    }

    @Test
    void completenessHeuristicHandlesBalancedInput() {
        assertFalse(PromptShell.isComplete("var a = (1 + 2"));   // open paren
        assertFalse(PromptShell.isComplete("{ var a = 1;"));     // open brace
        assertFalse(PromptShell.isComplete("print 1"));          // no terminator
        assertTrue(PromptShell.isComplete("print 1;"));
        assertTrue(PromptShell.isComplete("{ var a = 1; }"));
        assertFalse(PromptShell.isComplete("print \"a;"));       // unterminated string
    }
}
