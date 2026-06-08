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

class SelfHostExecutorTest {
    private static final Path SCANNER_PATH = Path.of("selfhost/scanner.cfab");
    private static final Path PARSER_PATH = Path.of("selfhost/parser.cfab");
    private static final Path EXECUTOR_PATH = Path.of("selfhost/executor.cfab");

    private static List<String> execute(String sourceExpression) throws IOException {
        String scanner = Files.readString(SCANNER_PATH, StandardCharsets.UTF_8);
        String parser = Files.readString(PARSER_PATH, StandardCharsets.UTF_8);
        String executor = Files.readString(EXECUTOR_PATH, StandardCharsets.UTF_8);
        String program = scanner + "\n" + parser + "\n" + executor + "\n"
                + "var tokens = scan(" + sourceExpression + ");\n"
                + "var statements = parse(tokens);\n"
                + "var output = execute(statements);\n"
                + "for (var i = 0; i < len(output); i = i + 1) {\n"
                + "  print output[i];\n"
                + "}\n";

        RunResult result = new CodeFab().run(program);
        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        return result.output();
    }

    private static String cfString(String value) {
        return "\"" + value + "\"";
    }

    @Test
    @DisplayName("selfhost executor runs print literals and arithmetic")
    void executesPrintLiteralsAndArithmetic() throws IOException {
        assertEquals(List.of("7", "hi", "true"),
            execute("\"print 1 + 2 * 3; print \" + chr(34) + \"hi\" + chr(34) + \"; print true;\""));
    }

    @Test
    @DisplayName("selfhost executor runs var declarations and variable reads")
    void executesVarDeclarationsAndVariableReads() throws IOException {
        assertEquals(List.of("12", "15"),
            execute(cfString("var total = 12; print total; var next = total + 3; print next;")));
    }

    @Test
    @DisplayName("selfhost executor runs assignment and while loops")
    void executesAssignmentAndWhileLoops() throws IOException {
        assertEquals(List.of("0", "1", "2"),
            execute(cfString("var n = 0; while (n < 3) { print n; n = n + 1; }")));
    }

    @Test
    @DisplayName("selfhost executor runs if else branches")
    void executesIfElseBranches() throws IOException {
        assertEquals(List.of("yes", "fallback"),
            execute("\"if (1 < 2) { print \" + chr(34) + \"yes\" + chr(34) + \"; } else { print \""
                + " + chr(34) + \"no\" + chr(34) + \"; } if (false) { print \" + chr(34) + \"no\""
                + " + chr(34) + \"; } else { print \" + chr(34) + \"fallback\" + chr(34) + \"; }\""));
    }

    @Test
    @DisplayName("selfhost executor keeps block scopes separate")
    void keepsBlockScopesSeparate() throws IOException {
        assertEquals(List.of("inner", "global"),
            execute("\"var x = \" + chr(34) + \"global\" + chr(34) + \"; { var x = \""
                + " + chr(34) + \"inner\" + chr(34) + \"; print x; } print x;\""));
    }

    @Test
    @DisplayName("selfhost executor calls functions and prints return values")
    void callsFunctionsAndPrintsReturnValues() throws IOException {
        assertEquals(List.of("7"),
            execute(cfString("Func add(a, b) { return a + b; } print add(3, 4);")));
    }

    @Test
    @DisplayName("selfhost executor keeps function body side effects")
    void keepsFunctionBodySideEffects() throws IOException {
        assertEquals(List.of("5"),
            execute(cfString("Func show(n) { print n; } show(5);")));
    }

    @Test
    @DisplayName("selfhost executor supports recursive calls")
    void supportsRecursiveCalls() throws IOException {
        assertEquals(List.of("120"),
            execute(cfString("Func fact(n) { if (n <= 1) { return 1; } return n * fact(n - 1); } print fact(5);")));
    }

    @Test
    @DisplayName("selfhost executor creates arrays and reads/writes indexes")
    void createsArraysAndReadsWritesIndexes() throws IOException {
        assertEquals(List.of("30"),
            execute(cfString("var a = Array(3); a[0] = 10; a[1] = 20; print a[0] + a[1];")));
    }

    @Test
    @DisplayName("selfhost executor runs for loops")
    void runsForLoops() throws IOException {
        assertEquals(List.of("6"),
            execute(cfString("var sum = 0; for (var i = 0; i < 4; i = i + 1) { sum = sum + i; } print sum;")));
    }

    @Test
    @DisplayName("selfhost executor keeps for loop variables scoped")
    void keepsForLoopVariablesScoped() throws IOException {
        assertEquals(List.of("99"),
            execute(cfString("var i = 99; for (var i = 0; i < 2; i = i + 1) { } print i;")));
    }

    @Test
    @DisplayName("selfhost executor short-circuits logical expressions")
    void shortCircuitsLogicalExpressions() throws IOException {
        assertEquals(List.of("0", "0", "true"),
            execute(cfString("var x = 0; true or (x = 1); print x; false and (x = 2); print x; print false or true;")));
    }
}
