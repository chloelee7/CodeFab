package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfHostRunnerTest {
    private static final Path SCANNER_PATH = Path.of("selfhost/scanner.cfab");
    private static final Path PARSER_PATH = Path.of("selfhost/parser.cfab");
    private static final Path CHECKER_PATH = Path.of("selfhost/checker.cfab");
    private static final Path EXECUTOR_PATH = Path.of("selfhost/executor.cfab");
    private static final Path RUNNER_PATH = Path.of("selfhost/runner.cfab");

    private static List<String> runSelfhost(String sourceExpression) throws IOException {
        String scanner = Files.readString(SCANNER_PATH, StandardCharsets.UTF_8);
        String parser = Files.readString(PARSER_PATH, StandardCharsets.UTF_8);
        String checker = Files.readString(CHECKER_PATH, StandardCharsets.UTF_8);
        String executor = Files.readString(EXECUTOR_PATH, StandardCharsets.UTF_8);
        String runner = Files.readString(RUNNER_PATH, StandardCharsets.UTF_8);
        String program = scanner + "\n" + parser + "\n" + checker + "\n" + executor + "\n" + runner + "\n"
                + "var result = run(" + sourceExpression + ");\n"
                + "print result[0];\n"
                + "var output = result[1];\n"
                + "for (var i = 0; i < len(output); i = i + 1) {\n"
                + "  print output[i];\n"
                + "}\n"
                + "var diagnostics = result[2];\n"
                + "for (var j = 0; j < len(diagnostics); j = j + 1) {\n"
                + "  print diagnostics[j];\n"
                + "}\n";

        RunResult result = new CodeFab().run(program);
        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        return result.output();
    }

    private static String cfString(String value) {
        return "\"" + value + "\"";
    }

    private static String cfStringWithQuotedLiteral(String before, String literal, String after) {
        return "\"" + before + "\" + chr(34) + \"" + literal + "\" + chr(34) + \"" + after + "\"";
    }

    @Test
    @DisplayName("selfhost runner executes valid programs")
    void executesValidPrograms() throws IOException {
        assertEquals(List.of("true", "3"),
            runSelfhost(cfString("var sum = 0; for (var i = 0; i < 3; i = i + 1) { sum = sum + i; } print sum;")));
    }

    @Test
    @DisplayName("selfhost runner stops on checker diagnostics")
    void stopsOnCheckerDiagnostics() throws IOException {
        assertEquals(List.of("false", "[CHECKER, 1, Can't return from top-level code.]"),
            runSelfhost(cfString("return 1;")));
    }

    @Test
    @DisplayName("selfhost runner stops on scanner diagnostics")
    void stopsOnScannerDiagnostics() throws IOException {
        assertEquals(List.of("false", "[SCANNER, 1, unexpected character '@']"),
            runSelfhost(cfString("@")));
    }

    @Test
    @DisplayName("selfhost runner stops on parser diagnostics")
    void stopsOnParserDiagnostics() throws IOException {
        List<String> result = runSelfhost(cfString("print 1"));

        assertEquals("false", result.get(0));
        assertTrue(result.contains("[PARSER, 1, Expect ';' after value. at end]"), () -> result.toString());
    }

    @Test
    @DisplayName("selfhost runner stops on runtime diagnostics")
    void stopsOnRuntimeDiagnostics() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Undefined variable 'missing'.]"),
            runSelfhost(cfString("print missing;")));
    }

    @Test
    @DisplayName("selfhost runner reports diagnostic source lines")
    void reportsDiagnosticSourceLines() throws IOException {
        assertEquals(List.of("false", "[CHECKER, 2, Can't return from top-level code.]"),
            runSelfhost(cfString("print 1;\nreturn 1;")));
        assertEquals(List.of("false", "1", "[RUNTIME, 2, Undefined variable 'missing'.]"),
            runSelfhost(cfString("print 1;\nprint missing;")));
    }

    @Test
    @DisplayName("selfhost runner reports calling non-functions")
    void reportsCallingNonFunctions() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Can only call functions.]"),
            runSelfhost(cfString("print 3();")));
    }

    @Test
    @DisplayName("selfhost runner reports arity mismatches")
    void reportsArityMismatches() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Expected 2 arguments but got 1.]"),
            runSelfhost(cfString("Func add(a, b) { return a + b; } print add(1);")));
    }

    @Test
    @DisplayName("selfhost runner reports arity before evaluating arguments")
    void reportsArityBeforeEvaluatingArguments() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Expected 0 arguments but got 1.]"),
            runSelfhost(cfString("Func f() { return 1; } print f(missing);")));
    }

    @Test
    @DisplayName("selfhost runner reports mixed addition type errors")
    void reportsMixedAdditionTypeErrors() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Operands must be two numbers or two strings.]"),
            runSelfhost(cfStringWithQuotedLiteral("print 1 + ", "HI", ";")));
    }

    @Test
    @DisplayName("selfhost runner reports numeric operand runtime errors")
    void reportsNumericOperandRuntimeErrors() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Operand must be a number.]"),
            runSelfhost(cfStringWithQuotedLiteral("print -", "FabCoding", ";")));
        assertEquals(List.of("false", "[RUNTIME, 1, Operands must be numbers.]"),
            runSelfhost(cfStringWithQuotedLiteral("print ", "a", " < 1;")));
    }

    @Test
    @DisplayName("selfhost runner reports division by zero")
    void reportsDivisionByZero() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Division by zero.]"),
            runSelfhost(cfString("print 3 / 0;")));
        assertEquals(List.of("false", "[RUNTIME, 1, Division by zero.]"),
            runSelfhost(cfString("print 3 % 0;")));
    }

    @Test
    @DisplayName("selfhost runner reports array access runtime errors")
    void reportsArrayAccessRuntimeErrors() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Only arrays can be indexed.]"),
            runSelfhost(cfString("var a = 3; print a[0];")));
        assertEquals(List.of("false", "[RUNTIME, 1, Array index must be a number.]"),
            runSelfhost(cfStringWithQuotedLiteral("var a = Array(1); print a[", "x", "];")));
        assertEquals(List.of("false", "[RUNTIME, 1, Array index must be an integer.]"),
            runSelfhost(cfString("var a = Array(1); print a[0.5];")));
        assertEquals(List.of("false", "[RUNTIME, 1, Array index 2 out of bounds (size 1).]"),
            runSelfhost(cfString("var a = Array(1); print a[2];")));
    }

    @Test
    @DisplayName("selfhost runner reports array assignment runtime errors")
    void reportsArrayAssignmentRuntimeErrors() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Only arrays can be indexed.]"),
            runSelfhost(cfString("var a = 3; a[0] = 1;")));
        assertEquals(List.of("false", "[RUNTIME, 1, Array index must be a number.]"),
            runSelfhost(cfStringWithQuotedLiteral("var a = Array(1); a[", "x", "] = 1;")));
        assertEquals(List.of("false", "[RUNTIME, 1, Array index must be an integer.]"),
            runSelfhost(cfString("var a = Array(1); a[0.5] = 1;")));
        assertEquals(List.of("false", "[RUNTIME, 1, Array index 2 out of bounds (size 1).]"),
            runSelfhost(cfString("var a = Array(1); a[2] = 1;")));
    }

    @Test
    @DisplayName("selfhost runner reports native helper runtime errors")
    void reportsNativeHelperRuntimeErrors() throws IOException {
        assertEquals(List.of("false", "[RUNTIME, 1, Array size must be a number.]"),
            runSelfhost(cfStringWithQuotedLiteral("var a = Array(", "two", ");")));
        assertEquals(List.of("false", "[RUNTIME, 1, Array size must be an integer.]"),
            runSelfhost(cfString("var a = Array(2.5);")));
        assertEquals(List.of("false", "[RUNTIME, 1, Array size must be non-negative.]"),
            runSelfhost(cfString("var a = Array(-1);")));
        assertEquals(List.of("false", "[RUNTIME, 1, len() expects a string or array.]"),
            runSelfhost(cfString("print len(123);")));
        assertEquals(List.of("false", "[RUNTIME, 1, charAt() expects a string.]"),
            runSelfhost(cfString("print charAt(123, 0);")));
        assertEquals(List.of("false", "[RUNTIME, 1, String index out of bounds.]"),
            runSelfhost(cfStringWithQuotedLiteral("print charAt(", "a", ", 1);")));
        assertEquals(List.of("false", "[RUNTIME, 1, String index must be an integer.]"),
            runSelfhost(cfStringWithQuotedLiteral("print charAt(", "a", ", 0.5);")));
        assertEquals(List.of("false", "[RUNTIME, 1, slice() expects a string.]"),
            runSelfhost(cfString("print slice(123, 0, 1);")));
        assertEquals(List.of("false", "[RUNTIME, 1, String slice out of bounds.]"),
            runSelfhost(cfStringWithQuotedLiteral("print slice(", "abc", ", 2, 1);")));
        assertEquals(List.of("false", "[RUNTIME, 1, push() expects an array.]"),
            runSelfhost(cfStringWithQuotedLiteral("print push(", "not array", ", 1);")));
        assertEquals(List.of("false", "[RUNTIME, 1, chr() expects an integer character code.]"),
            runSelfhost(cfStringWithQuotedLiteral("print chr(", "quote", ");")));
        assertEquals(List.of("false", "[RUNTIME, 1, num() expects a numeric string.]"),
            runSelfhost(cfString("print num(12);")));
        assertEquals(List.of("false", "[RUNTIME, 1, num() expects a numeric string.]"),
            runSelfhost(cfStringWithQuotedLiteral("print num(", "abc", ");")));
        assertEquals(List.of("false", "[RUNTIME, 1, ord() expects a one-character string.]"),
            runSelfhost(cfString("print ord(12);")));
        assertEquals(List.of("false", "[RUNTIME, 1, ord() expects a one-character string.]"),
            runSelfhost(cfStringWithQuotedLiteral("print ord(", "ab", ");")));
    }
}
