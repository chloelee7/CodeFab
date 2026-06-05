package codefab;

import codefab.core.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests for static arrays: creation, indexed read/write, expression indices, and runtime errors. */
class ArrayTest {

    private RunResult run(String src) {
        return new CodeFab().run(src);
    }

    private List<String> out(String src) {
        RunResult r = run(src);
        assertTrue(r.success(), () -> "expected success but got diagnostics: " + r.diagnostics());
        return r.output();
    }

    private String single(String src) {
        List<String> lines = out(src);
        assertEquals(1, lines.size(), () -> "expected single line, got " + lines);
        return lines.get(0);
    }

    private void assertFailsAtStage(String src, Diagnostic.Stage stage, String substring) {
        RunResult r = run(src);
        assertFalse(r.success(), () -> "expected failure for: " + src);
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.stage == stage && d.message.contains(substring)),
                () -> "expected " + stage + " diagnostic containing '" + substring + "' but got: " + r.diagnostics());
    }

    // --- Normal operation --------------------------------------------------

    @Test
    void createAssignAndReadFirstElement() {
        String src = "var arr = Array(3); arr[0] = 10; arr[1] = 20; arr[2] = 30; print arr[0];";
        assertEquals("10", single(src));
    }

    @Test
    void expressionAsIndexResolvesCorrectSlot() {
        String src = "var arr = Array(3); var i = 2; arr[i - 1] = 7; print arr[1];";
        assertEquals("7", single(src));
    }

    @Test
    void freshlyCreatedArrayElementsAreNil() {
        String src = "var a = Array(2); print a[0];";
        assertEquals("nil", single(src));
    }

    @Test
    void writeAndReadLastIndex() {
        String src = "var a = Array(3); a[2] = 99; print a[2];";
        assertEquals("99", single(src));
    }

    // --- Runtime errors ----------------------------------------------------

    @Test
    void indexOutOfBoundsIsRuntimeError() {
        assertFailsAtStage("var a = Array(3); print a[5];", Diagnostic.Stage.RUNTIME,
                "Array index out of bounds.");
    }

    @Test
    void nonNumericIndexIsRuntimeError() {
        assertFailsAtStage("var a = Array(3); print a[\"hello\"];", Diagnostic.Stage.RUNTIME,
                "Array index must be a number.");
    }

    @Test
    void indexingNonArrayTargetIsRuntimeError() {
        assertFailsAtStage("var x = 10; print x[0];", Diagnostic.Stage.RUNTIME,
                "Can only index arrays.");
    }

    @Test
    void nonNumericArraySizeIsRuntimeError() {
        assertFailsAtStage("var b = Array(\"hi\");", Diagnostic.Stage.RUNTIME,
                "Array size must be a number.");
    }
}
