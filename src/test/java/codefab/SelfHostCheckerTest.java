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

class SelfHostCheckerTest {
    private static final Path SCANNER_PATH = Path.of("selfhost/scanner.cfab");
    private static final Path PARSER_PATH = Path.of("selfhost/parser.cfab");
    private static final Path CHECKER_PATH = Path.of("selfhost/checker.cfab");

    private static List<String> check(String sourceExpression) throws IOException {
        String scanner = Files.readString(SCANNER_PATH, StandardCharsets.UTF_8);
        String parser = Files.readString(PARSER_PATH, StandardCharsets.UTF_8);
        String checker = Files.readString(CHECKER_PATH, StandardCharsets.UTF_8);
        String program = scanner + "\n" + parser + "\n" + checker + "\n"
                + "var tokens = scan(" + sourceExpression + ");\n"
                + "var statements = parse(tokens);\n"
                + "var diagnostics = check(statements);\n"
                + "for (var i = 0; i < len(diagnostics); i = i + 1) {\n"
                + "  print diagnostics[i];\n"
                + "}\n";

        RunResult result = new CodeFab().run(program);
        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        return result.output();
    }

    private static String cfString(String value) {
        return "\"" + value + "\"";
    }

    @Test
    @DisplayName("selfhost checker reports top-level return")
    void reportsTopLevelReturn() throws IOException {
        assertEquals(List.of("[CHECKER, 1, Can't return from top-level code.]"),
            check(cfString("return 1;")));
    }

    @Test
    @DisplayName("selfhost checker reports duplicate local declarations")
    void reportsDuplicateLocalDeclarations() throws IOException {
        assertEquals(List.of("[CHECKER, 1, Already a variable with this name in this scope.]"),
            check(cfString("{ var a = 1; var a = 2; }")));
    }

    @Test
    @DisplayName("selfhost checker reports self reads in initializers")
    void reportsSelfReadsInInitializers() throws IOException {
        assertEquals(List.of("[CHECKER, 1, Can't read local variable in initializer.]"),
            check(cfString("{ var a = a; }")));
    }

    @Test
    @DisplayName("selfhost checker reports duplicate parameter names")
    void reportsDuplicateParameterNames() throws IOException {
        assertTrue(
            check(cfString("Func f(a, a) { print a; }")).contains(
                "[CHECKER, 1, Already a parameter with this name.]"));
    }

    @Test
    @DisplayName("selfhost checker accepts valid recursive functions")
    void acceptsValidRecursiveFunctions() throws IOException {
        assertEquals(List.of(),
            check(cfString("Func fact(n) { if (n <= 1) { return 1; } return n * fact(n - 1); } print fact(5);")));
    }
}
