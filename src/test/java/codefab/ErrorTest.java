package codefab;

import codefab.core.Diagnostic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests: every program here must fail with a meaningful diagnostic. */
class ErrorTest {

    private RunResult run(String src) {
        return new CodeFab().run(src);
    }

    private void assertFailsWith(String src, String substring) {
        RunResult r = run(src);
        assertFalse(r.success(), () -> "expected failure for: " + src);
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message.contains(substring)),
                () -> "expected diagnostic containing '" + substring + "' but got: " + r.diagnostics());
    }

    private void assertFailsAtStage(String src, Diagnostic.Stage stage, String substring) {
        RunResult r = run(src);
        assertFalse(r.success());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.stage == stage && d.message.contains(substring)),
                () -> "expected " + stage + " diagnostic containing '" + substring + "' but got: " + r.diagnostics());
    }

    // --- Syntax errors -----------------------------------------------------

    @Test
    void missingSemicolon() {
        assertFailsAtStage("print 1 + 2", Diagnostic.Stage.PARSER, "Expect ';' after value.");
    }

    @Test
    void missingClosingParen() {
        assertFailsAtStage("print (1 + 2;", Diagnostic.Stage.PARSER, "Expect ')' after expression.");
    }

    @Test
    void invalidAssignmentTarget() {
        assertFailsAtStage("var a = 1;\nvar b = 2;\na + b = 3;", Diagnostic.Stage.PARSER,
                "Invalid assignment target.");
    }

    @Test
    void invalidExpressionStart() {
        assertFailsAtStage("print * 5;", Diagnostic.Stage.PARSER, "Expect expression.");
    }

    // --- Checker errors ----------------------------------------------------

    @Test
    void readLocalInOwnInitializer() {
        assertFailsAtStage("{\n  var a = a;\n}", Diagnostic.Stage.CHECKER,
                "Can't read local variable in initializer.");
    }

    @Test
    void duplicateDeclarationInSameScope() {
        assertFailsAtStage("{\n  var a = \"hi\";\n  var a = 3;\n}", Diagnostic.Stage.CHECKER,
                "Already a variable with this name in this scope.");
    }

    @Test
    void checkerErrorPreventsExecution() {
        // The print should never run because the checker fails first.
        RunResult r = run("{\n  var a = a;\n  print \"reached\";\n}");
        assertFalse(r.success());
        assertTrue(r.output().isEmpty(), () -> "executor must not run: " + r.output());
    }

    // --- Runtime errors ----------------------------------------------------

    @Test
    void undefinedVariable() {
        assertFailsAtStage("print notDefined;", Diagnostic.Stage.RUNTIME,
                "Undefined variable 'notDefined'.");
    }

    @Test
    void assignmentToUndefinedVariable() {
        assertFailsAtStage("undefinedVar = 1;", Diagnostic.Stage.RUNTIME,
                "Undefined variable 'undefinedVar'.");
    }

    @Test
    void mixedTypeAddition() {
        assertFailsAtStage("print 1 + \"HI\";", Diagnostic.Stage.RUNTIME,
                "Operands must be two numbers or two strings.");
    }

    @Test
    void negateString() {
        assertFailsAtStage("print -\"FabCoding\";", Diagnostic.Stage.RUNTIME,
                "Operand must be a number.");
    }

    @Test
    void divisionByZero() {
        assertFailsAtStage("print 3 / 0;", Diagnostic.Stage.RUNTIME, "Division by zero.");
    }

    @Test
    void comparisonRequiresNumbers() {
        assertFailsAtStage("print \"a\" < 1;", Diagnostic.Stage.RUNTIME, "Operands must be numbers.");
    }
}
