package codefab;

import codefab.core.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests for function declaration, call, parameters, return, and recursion. */
class FunctionTest {

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
    void declareCallParametersAndAssignReturnValue() {
        String src = "Func add(a, b) { return a + b; }\nvar ret = add(3, 7);\nprint ret;";
        assertEquals(List.of("10"), out(src));
    }

    @Test
    void returnValueUsedDirectlyInExpression() {
        String src = "Func add(a, b) { return a + b; }\nprint add(3, 7);";
        assertEquals(List.of("10"), out(src));
    }

    @Test
    void emptyReturnYieldsNil() {
        String src = "Func nothing() { return ; }\nprint nothing();";
        assertEquals(List.of("nil"), out(src));
    }

    @Test
    void functionWithNoReturnYieldsNil() {
        String src = "Func noop() { var x = 1; }\nprint noop();";
        assertEquals(List.of("nil"), out(src));
    }

    @Test
    void recursiveFactorial() {
        String src = "Func fact(n) {\n  if (n <= 1) return 1;\n  return n * fact(n - 1);\n}\nprint fact(5);";
        assertEquals(List.of("120"), out(src));
    }

    @Test
    void zeroArgumentFunction() {
        String src = "Func greet() { return \"hi\"; }\nprint greet();";
        assertEquals("hi", single(src));
    }

    @Test
    void multipleArgumentFunction() {
        String src = "Func sum3(a, b, c) { return a + b + c; }\nprint sum3(1, 2, 3);";
        assertEquals("6", single(src));
    }

    // --- Checker errors ----------------------------------------------------

    @Test
    void returnOutsideFunctionIsCheckerError() {
        assertFailsAtStage("return 5;", Diagnostic.Stage.CHECKER, "Can't return from top-level code.");
    }

    @Test
    void duplicateParameterNameIsCheckerError() {
        assertFailsAtStage("Func foo(a, a) { return a; }", Diagnostic.Stage.CHECKER,
                "Already a variable with this name in this scope.");
    }

    // --- Runtime errors ----------------------------------------------------

    @Test
    void callingNonFunctionIsRuntimeError() {
        assertFailsAtStage("var x = \"hello\";\nx();", Diagnostic.Stage.RUNTIME, "Can only call functions.");
    }

    @Test
    void argumentCountMismatchIsRuntimeError() {
        assertFailsAtStage("Func foo(a, b, c) { return a; }\nfoo(1, 2);", Diagnostic.Stage.RUNTIME,
                "Expected 3 arguments but got 2.");
    }
}
