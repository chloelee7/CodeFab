package codefab;

import codefab.core.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests for the {@code while} statement, asserting captured output and diagnostics. */
class WhileTest {

    private RunResult run(String src) {
        return new CodeFab().run(src);
    }

    private List<String> out(String src) {
        RunResult r = run(src);
        assertTrue(r.success(), () -> "expected success but got diagnostics: " + r.diagnostics());
        return r.output();
    }

    @Test
    void whileLoopRepeatsBodyWhileConditionTrue() {
        String src = "var i = 0; while (i < 3) { print i; i = i + 1; }";
        assertEquals(List.of("0", "1", "2"), out(src));
    }

    @Test
    void whileWithInitiallyFalseConditionNeverRunsBody() {
        RunResult r = run("while (false) print \"x\";");
        assertTrue(r.success(), () -> "expected success but got diagnostics: " + r.diagnostics());
        assertTrue(r.output().isEmpty(), () -> "expected no output, got " + r.output());
    }

    @Test
    void whileBodyCanBeSingleStatementNotBlock() {
        String src = "var i = 0; while (i < 2) i = i + 1; print i;";
        assertEquals(List.of("2"), out(src));
    }

    @Test
    void nestedWhileLoopsProducesCartesianOutput() {
        String src = "var i = 0;\n"
                + "while (i < 2) {\n"
                + "  var j = 0;\n"
                + "  while (j < 2) {\n"
                + "    print i * 10 + j;\n"
                + "    j = j + 1;\n"
                + "  }\n"
                + "  i = i + 1;\n"
                + "}";
        assertEquals(List.of("0", "1", "10", "11"), out(src));
    }

    @Test
    void whileBodyUpdatesOuterVariable() {
        String src = "var sum = 0;\n"
                + "var n = 4;\n"
                + "while (n > 0) {\n"
                + "  sum = sum + n;\n"
                + "  n = n - 1;\n"
                + "}\n"
                + "print sum;";
        assertEquals(List.of("10"), out(src));
    }

    @Test
    void whileWithoutOpeningParenReportsParserDiagnostic() {
        RunResult r = run("while true print 1;");
        assertFalse(r.success(), () -> "expected failure but succeeded with output: " + r.output());
        assertTrue(r.diagnostics().stream()
                        .anyMatch(d -> d.stage == Diagnostic.Stage.PARSER
                                && d.message.contains("Expect '(' after 'while'.")),
                () -> "expected PARSER diagnostic \"Expect '(' after 'while'.\", got: " + r.diagnostics());
    }
}
