package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import codefab.core.Diagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfHostParityTest {
    private static void assertParity(String source) {
        RunResult javaResult = new CodeFab().run(source);
        RunResult selfhostResult = new SelfHostCodeFab().run(source);

        assertEquals(javaResult.success(), selfhostResult.success(), "success");
        assertEquals(javaResult.output(), selfhostResult.output(), "output");
        assertEquals(renderDiagnostics(javaResult.diagnostics()), renderDiagnostics(selfhostResult.diagnostics()),
            "diagnostics");
    }

    private static List<String> renderDiagnostics(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
            .map(diagnostic -> diagnostic.stage + ":" + diagnostic.line + ":" + diagnostic.message)
            .toList();
    }

    @Test
    @DisplayName("selfhost matches Java for representative valid programs")
    void matchesJavaForRepresentativeValidPrograms() {
        assertParity("print 1 + 2 * 3;");
        assertParity("var sum = 0; for (var i = 0; i < 4; i = i + 1) { sum = sum + i; } print sum;");
        assertParity("Func fact(n) { if (n <= 1) { return 1; } return n * fact(n - 1); } print fact(5);");
        assertParity("var a = Array(2); a[0] = 10; a[1] = 20; print a[0] + a[1];");
        assertParity("var x = 0; true or (x = 1); false and (x = 2); print x;");
        assertParity("print nil; print true == false; print nil == nil;");
        assertParity("var a = Array(2); print a; print push(a, 3); print a;");
        assertParity("print charAt(\"CodeFab\", 4); print slice(\"CodeFab\", 4, 7); print num(\"3.5\") + 0.5;");
    }

    @Test
    @DisplayName("selfhost matches Java for num native parse formats")
    void matchesJavaForNumNativeParseFormats() {
        assertParity("print num(\"1e2\");");
        assertParity("print num(\"+2\");");
        assertParity("print num(\"NaN\");");
        assertParity("print num(\"Infinity\");");
        assertParity("print num(\"-Infinity\");");
        assertParity("print num(\"1d\");");
        assertParity("print num(\"1f\");");
        assertParity("print num(\"0x1.0p0\");");
        assertParity("print num(\"0x.8p1\");");
    }

    @Test
    @DisplayName("selfhost matches Java for end-to-end valid contracts")
    void matchesJavaForEndToEndValidContracts() {
        assertParity("print (1 + 2) * 3;");
        assertParity("print 10 - 4 - 3; print 8 / 2 / 2;");
        assertParity("print -3 + 2; print +5;");
        assertParity("print 1 < 2; print 3 > 5; print true; print false;");
        assertParity("print \"Hello, \" + \"CodeFab!\";");
        assertParity("print 5; print 5.0; print 3.14;");
        assertParity("var a = 10; var b = 20; print a + b; a = a + 5; print a;");
        assertParity("var x = \"global\";\n{\n  var x = \"inner\";\n  print x;\n}\nprint x;");
        assertParity("var count = 0;\n{\n  count = count + 1;\n}\nprint count;");
        assertParity("var outer = \"A\";\n{\n  var inner = \"B\";\n  {\n    print outer + inner;\n  }\n}");
        assertParity("if (true) print \"bbq\"; if (false) print \"no\";");
        assertParity("if (false) print \"no\"; else print \"kfc\";");
        assertParity("if (true)\n  if (false) print \"kfc\";\n  else print \"bbq\";");
        assertParity("for (var j = 0; j < 3; j = j + 1) {\n  print j;\n}");
        assertParity("// header comment\nprint 1; // trailing comment");
        assertParity("print true or false; print true and false;");
        assertParity("Func greet() { print \"hello\"; } greet();");
        assertParity("Func add(a, b) { return a + b; } print add(3, 4);");
        assertParity("Func f() { return; } print f();");
        assertParity("var a = Array(3); a[0] = 10; a[1] = 20; print a[0] + a[1];");
        assertParity("print 3 + 7; print 2 * 3; print 10 - 5; print 2 + 3 * 4;");
    }

    @Test
    @DisplayName("selfhost matches Java for end-to-end diagnostic contracts")
    void matchesJavaForEndToEndDiagnosticContracts() {
        assertParity("print 1 + 2");
        assertParity("print (1 + 2;");
        assertParity("var a = 1;\nvar b = 2;\na + b = 3;");
        assertParity("print * 5;");
        assertParity("{\n  var a = a;\n}");
        assertParity("{\n  var a = \"hi\";\n  var a = 3;\n}");
        assertParity("{\n  var a = a;\n  print \"reached\";\n}");
        assertParity("print notDefined;");
        assertParity("undefinedVar = 1;");
        assertParity("print 1 + \"HI\";");
        assertParity("print -\"FabCoding\";");
        assertParity("print 3 / 0;");
        assertParity("print \"a\" < 1;");
        assertParity("for (var j = 0; j < 1; j = j + 1) { print j; }\nprint j;");
        assertParity("var a = Array(2); print a[5];");
        assertParity("var a = 3; print a[0];");
    }

    @Test
    @DisplayName("selfhost example program matches Java output")
    void selfhostExampleProgramMatchesJavaOutput() throws IOException {
        String source = Files.readString(Path.of("examples/selfhost_showcase.cfab"), StandardCharsets.UTF_8);

        assertParity(source);
    }

    @Test
    @DisplayName("selfhost matches Java for representative scanner diagnostics")
    void matchesJavaForRepresentativeScannerDiagnostics() {
        assertParity("@");
        assertParity("print \"unterminated");
    }

    @Test
    @DisplayName("selfhost matches Java for representative checker diagnostics")
    void matchesJavaForRepresentativeCheckerDiagnostics() {
        assertParity("return 1;");
        assertParity("{\nvar a = 1;\nvar a = 2;\n}");
        assertParity("{\nvar a = a;\n}");
        assertParity("Func f(a, a) { print a; }");
        assertParity("{\nvar a = 1;\nvar a = 2;\nvar b = b;\n}");
        assertParity("Func f(\n  a,\n  a\n) { print a; }");
    }

    @Test
    @DisplayName("selfhost matches Java for representative parser diagnostics")
    void matchesJavaForRepresentativeParserDiagnostics() {
        assertParity("print 1");
        assertParity("print ;\nprint 2;");
        assertParity("1 = 2;");
        assertParity("if true) print 1;");
        assertParity("print (1 + 2;");
        assertParity("Func f(a,) { print a; }");
    }

    @Test
    @DisplayName("selfhost matches Java for representative runtime diagnostics")
    void matchesJavaForRepresentativeRuntimeDiagnostics() {
        assertParity("print 1;\nprint missing;");
        assertParity("print 1 + \"HI\";");
        assertParity("print -\"FabCoding\";");
        assertParity("print 3 / 0;");
        assertParity("var a = Array(1); print a[2];");
        assertParity("print len(123);");
        assertParity("print 3 % 0;");
        assertParity("var a = Array(1); a[0.5] = 1;");
        assertParity("print charAt(\"a\", 1);");
        assertParity("print num(\"abc\");");
        assertParity("print 3();");
        assertParity("Func f() { return 1; } print f(missing);");
        assertParity("Func recurse() { recurse(); } recurse();");
    }
}
