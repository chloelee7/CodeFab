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

class SelfHostScannerTest {
    private static final Path SCANNER_PATH = Path.of("selfhost/scanner.cfab");

    private static List<String> scan(String sourceExpression) throws IOException {
        String scanner = Files.readString(SCANNER_PATH, StandardCharsets.UTF_8);
        String program = scanner + "\n"
                + "var tokens = scan(" + sourceExpression + ");\n"
                + "for (var i = 0; i < len(tokens); i = i + 1) {\n"
                + "  print tokens[i];\n"
                + "}\n";

        RunResult result = new CodeFab().run(program);
        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        return result.output();
    }

    private static String cfString(String value) {
        return "\"" + value + "\"";
    }

    @Test
    @DisplayName("selfhost scanner emits records for keywords, identifiers, numbers, and punctuation")
    void scansKeywordsIdentifiersNumbersAndPunctuation() throws IOException {
        assertEquals(List.of(
                "[VAR, var, nil, 1]",
                "[IDENTIFIER, total, nil, 1]",
                "[EQUAL, =, nil, 1]",
                "[NUMBER, 12, 12, 1]",
                "[SEMICOLON, ;, nil, 1]",
                "[PRINT, print, nil, 1]",
                "[IDENTIFIER, total, nil, 1]",
                "[SEMICOLON, ;, nil, 1]",
                "[EOF, , nil, 1]"),
            scan(cfString("var total = 12; print total;")));
    }

    @Test
    @DisplayName("selfhost scanner handles strings, comments, and line numbers")
    void scansStringsCommentsAndLineNumbers() throws IOException {
        String sourceExpression = "\"print \" + chr(34) + \"hi\" + chr(34)"
                + " + \"; // done\" + chr(10) + \"print 2;\"";

        assertEquals(List.of(
                "[PRINT, print, nil, 1]",
                "[STRING, \"hi\", hi, 1]",
                "[SEMICOLON, ;, nil, 1]",
                "[PRINT, print, nil, 2]",
                "[NUMBER, 2, 2, 2]",
                "[SEMICOLON, ;, nil, 2]",
                "[EOF, , nil, 2]"),
            scan(sourceExpression));
    }
}
