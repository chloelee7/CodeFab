package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codefab.core.Diagnostic;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NativeFunctionTest {

    private static RunResult run(String source) {
        return new CodeFab().run(source);
    }

    private static List<String> output(String source) {
        RunResult result = run(source);
        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        return result.output();
    }

    private static void assertRuntimeError(String source, String messagePart) {
        RunResult result = run(source);
        assertFalse(result.success(), () -> "expected failure, got output: " + result.output());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.stage == Diagnostic.Stage.RUNTIME && d.message.contains(messagePart)),
            () -> "expected RUNTIME diagnostic containing '" + messagePart + "', got: " + result.diagnostics());
    }

    @Test
    @DisplayName("len returns string and array lengths")
    void lenReturnsStringAndArrayLengths() {
        String source = """
                print len("CodeFab");
                var values = Array(2);
                values[0] = "a";
                values[1] = "b";
                print len(values);
                """;

        assertEquals(List.of("7", "2"), output(source));
    }

    @Test
    @DisplayName("charAt returns one-character strings")
    void charAtReturnsOneCharacterStrings() {
        String source = """
                print charAt("CodeFab", 0);
                print charAt("CodeFab", 4);
                print charAt("CodeFab", 6);
                """;

        assertEquals(List.of("C", "F", "b"), output(source));
    }

    @Test
    @DisplayName("slice returns substring with exclusive end")
    void sliceReturnsSubstringWithExclusiveEnd() {
        String source = """
                print slice("CodeFab", 0, 4);
                print slice("CodeFab", 4, 7);
                print slice("CodeFab", 2, 2);
                """;

        assertEquals(List.of("Code", "Fab", ""), output(source));
    }

    @Test
    @DisplayName("push appends to arrays and returns the new length")
    void pushAppendsToArraysAndReturnsNewLength() {
        String source = """
                var tokens = Array(0);
                print push(tokens, "IDENTIFIER");
                print push(tokens, "EOF");
                print len(tokens);
                print tokens[0];
                print tokens[1];
                """;

        assertEquals(List.of("1", "2", "2", "IDENTIFIER", "EOF"), output(source));
    }

    @Test
    @DisplayName("chr returns one-character strings by character code")
    void chrReturnsOneCharacterStringsByCharacterCode() {
        String source = """
                print chr(34);
                print len(chr(10));
                print "a" + chr(34) + "b";
                """;

        assertEquals(List.of("\"", "1", "a\"b"), output(source));
    }

    @Test
    @DisplayName("num parses numeric strings")
    void numParsesNumericStrings() {
        String source = """
                print num("12") + 1;
                print num("3.5") + 0.5;
                """;

        assertEquals(List.of("13", "4"), output(source));
    }

    @Test
    @DisplayName("ord returns character codes for one-character strings")
    void ordReturnsCharacterCodesForOneCharacterStrings() {
        String source = """
                print ord("0");
                print ord("A");
                print ord(chr(34));
                """;

        assertEquals(List.of("48", "65", "34"), output(source));
    }

    @Test
    @DisplayName("typeOf returns stable runtime type names")
    void typeOfReturnsStableRuntimeTypeNames() {
        String source = """
                Func f() { return 1; }
                var a = Array(1);
                print typeOf(nil);
                print typeOf(true);
                print typeOf(3);
                print typeOf("s");
                print typeOf(a);
                print typeOf(f);
                print typeOf(typeOf);
                """;

        assertEquals(List.of("nil", "Boolean", "Number", "String", "Array", "Function", "Function"),
            output(source));
    }

    @Test
    @DisplayName("valueText returns CodeFab output formatting")
    void valueTextReturnsCodeFabOutputFormatting() {
        String source = """
                var a = Array(2);
                a[0] = 1;
                print valueText(nil);
                print valueText(true);
                print valueText(3.5);
                print valueText("s");
                print valueText(a);
                """;

        assertEquals(List.of("nil", "true", "3.5", "s", "[1, nil]"), output(source));
    }

    @Test
    @DisplayName("native functions report stable runtime errors")
    void nativeFunctionsReportRuntimeErrors() {
        assertRuntimeError("print len(123);", "len() expects a string or array.");
        assertRuntimeError("print charAt(123, 0);", "charAt() expects a string.");
        assertRuntimeError("print charAt(\"a\", 1);", "String index out of bounds.");
        assertRuntimeError("print charAt(\"a\", 0.5);", "String index must be an integer.");
        assertRuntimeError("print slice(123, 0, 1);", "slice() expects a string.");
        assertRuntimeError("print slice(\"abc\", 2, 1);", "String slice out of bounds.");
        assertRuntimeError("print push(\"not array\", 1);", "push() expects an array.");
        assertRuntimeError("print chr(\"quote\");", "chr() expects an integer character code.");
        assertRuntimeError("print chr(1.5);", "chr() expects an integer character code.");
        assertRuntimeError("print num(12);", "num() expects a numeric string.");
        assertRuntimeError("print num(\"abc\");", "num() expects a numeric string.");
        assertRuntimeError("print ord(12);", "ord() expects a one-character string.");
        assertRuntimeError("print ord(\"ab\");", "ord() expects a one-character string.");
    }

    @Test
    @DisplayName("Array native function preserves existing behavior")
    void arrayNativeFunctionPreservesExistingBehavior() {
        assertEquals(List.of("[1, nil]"), output("var a = Array(2); a[0] = 1; print a;"));
        assertRuntimeError("var a = Array(\"two\");", "Array size must be a number.");
        assertRuntimeError("var a = Array(2.5);", "Array size must be an integer.");
        assertRuntimeError("var a = Array(-1);", "Array size must be non-negative.");
    }
}
