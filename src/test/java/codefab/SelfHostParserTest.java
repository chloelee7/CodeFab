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

class SelfHostParserTest {
    private static final Path SCANNER_PATH = Path.of("selfhost/scanner.cfab");
    private static final Path PARSER_PATH = Path.of("selfhost/parser.cfab");

    private static List<String> parse(String sourceExpression) throws IOException {
        String scanner = Files.readString(SCANNER_PATH, StandardCharsets.UTF_8);
        String parser = Files.readString(PARSER_PATH, StandardCharsets.UTF_8);
        String program = scanner + "\n" + parser + "\n"
                + "var tokens = scan(" + sourceExpression + ");\n"
                + "var statements = parse(tokens);\n"
                + "for (var i = 0; i < len(statements); i = i + 1) {\n"
                + "  print statements[i];\n"
                + "}\n";

        RunResult result = new CodeFab().run(program);
        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        return result.output();
    }

    private static String cfString(String value) {
        return "\"" + value + "\"";
    }

    @Test
    @DisplayName("selfhost parser emits AST records for var and print statements")
    void parsesVarAndPrintStatements() throws IOException {
        assertEquals(List.of(
                "[var, total, [literal, 12, 1], 1]",
                "[print, [variable, total, 1], 1]"),
            parse(cfString("var total = 12; print total;")));
    }

    @Test
    @DisplayName("selfhost parser preserves expression precedence")
    void parsesExpressionPrecedence() throws IOException {
        assertEquals(List.of(
                "[print, [binary, [literal, 1, 1], +, [binary, [literal, 2, 1], *, [literal, 3, 1], 1], 1], 1]"),
            parse(cfString("print 1 + 2 * 3;")));
    }

    @Test
    @DisplayName("selfhost parser emits AST records for function declarations and returns")
    void parsesFunctionDeclarationsAndReturns() throws IOException {
        assertEquals(List.of(
                "[function, add, [[a, 1], [b, 1]], [[return, [binary, [variable, a, 1], +, [variable, b, 1], 1], 1]], 1]",
                "[print, [call, [variable, add, 1], [[literal, 3, 1], [literal, 4, 1]], 1], 1]"),
            parse(cfString("Func add(a, b) { return a + b; } print add(3, 4);")));
    }

    @Test
    @DisplayName("selfhost parser emits AST records for array get and set")
    void parsesArrayGetAndSet() throws IOException {
        assertEquals(List.of(
                "[var, a, [call, [variable, Array, 1], [[literal, 2, 1]], 1], 1]",
                "[expr, [set, [variable, a, 1], [literal, 0, 1], [literal, 10, 1], 1], 1]",
                "[print, [get, [variable, a, 1], [literal, 0, 1], 1], 1]"),
            parse(cfString("var a = Array(2); a[0] = 10; print a[0];")));
    }

    @Test
    @DisplayName("selfhost parser emits AST records for for statements")
    void parsesForStatements() throws IOException {
        assertEquals(List.of(
                "[for, [var, i, [literal, 0, 1], 1], [binary, [variable, i, 1], <, [literal, 3, 1], 1], "
                    + "[assign, i, [binary, [variable, i, 1], +, [literal, 1, 1], 1], 1], "
                    + "[block, [[print, [variable, i, 1], 1]], 1], 1]"),
            parse(cfString("for (var i = 0; i < 3; i = i + 1) { print i; }")));
    }

    @Test
    @DisplayName("selfhost parser preserves logical operator precedence")
    void parsesLogicalOperatorPrecedence() throws IOException {
        assertEquals(List.of(
                "[print, [logical, [literal, false, 1], or, [logical, [literal, true, 1], and, [literal, false, 1], 1], 1], 1]"),
            parse(cfString("print false or true and false;")));
    }
}
