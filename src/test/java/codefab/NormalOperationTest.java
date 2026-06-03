package codefab;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests for valid programs, asserting captured stdout. */
class NormalOperationTest {

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

    @DisplayName("이항/단항/그룹 표현식 - 산술 우선순위")
    @Test
    void arithmeticPrecedence() {
        assertEquals("7", single("print 1 + 2 * 3;"));
        assertEquals("9", single("print (1 + 2) * 3;"));
        assertEquals("3", single("print 10 - 4 - 3;"));
        assertEquals("2", single("print 8 / 2 / 2;"));
        assertEquals("-1", single("print -3 + 2;"));
    }

    @DisplayName("이항/단항/그룹 표현식 - 비교 연산과 불리언")
    @Test
    void comparisonsAndBooleans() {
        assertEquals("true", single("print 1 < 2;"));
        assertEquals("false", single("print 3 > 5;"));
        assertEquals("true", single("print true;"));
        assertEquals("false", single("print false;"));
    }

    @DisplayName("이항/단항/그룹 표현식 - 문자열 연결")
    @Test
    void stringConcatenation() {
        assertEquals("Hello, CodeFab!", single("print \"Hello, \" + \"CodeFab!\";"));
    }

    @DisplayName("이항/단항/그룹 표현식 - 숫자 포맷")
    @Test
    void numberFormatting() {
        assertEquals("5", single("print 5;"));
        assertEquals("5", single("print 5.0;"));
        assertEquals("3.14", single("print 3.14;"));
    }

    @DisplayName("변수 재할당")
    @Test
    void variablesAndAssignment() {
        assertEquals(List.of("30"), out("var a = 10; var b = 20; print a + b;"));
        assertEquals(List.of("15"), out("var a = 10; a = a + 5; print a;"));
    }

    @DisplayName("블록 스코프 - 변수 섀도잉")
    @Test
    void blockScopeShadowing() {
        String src = "var x = \"global\";\n{\n  var x = \"inner\";\n  print x;\n}\nprint x;";
        assertEquals(List.of("inner", "global"), out(src));
    }

    @DisplayName("블록 스코프 - 외부 변수 접근")
    @Test
    void assignmentToOuterVariableInsideBlock() {
        String src = "var count = 0;\n{\n  count = count + 1;\n}\nprint count;";
        assertEquals(List.of("1"), out(src));
    }

    @DisplayName("블록 스코프 - 중첩 섀도잉")
    @Test
    void nestedShadowingReadsOuterVariables() {
        String src = "var outer = \"A\";\n{\n  var inner = \"B\";\n  {\n    print outer + inner;\n  }\n}";
        assertEquals(List.of("AB"), out(src));
    }

    @DisplayName("if/else 조건문 - else 없는 if")
    @Test
    void ifWithoutElse() {
        assertEquals(List.of("bbq"), out("if (true) print \"bbq\";"));
        assertTrue(out("if (false) print \"no\";").isEmpty());
    }

    @DisplayName("if/else 조건문")
    @Test
    void ifElse() {
        assertEquals(List.of("kfc"), out("if (false) print \"no\"; else print \"kfc\";"));
    }

    @DisplayName("if/else 조건문 - dangling else")
    @Test
    void danglingElseBindsToNearestIf() {
        String src = "if (true)\n  if (false) print \"kfc\";\n  else print \"bbq\";";
        assertEquals(List.of("bbq"), out(src));
    }

    @DisplayName("for 루프")
    @Test
    void forLoop() {
        String src = "for (var j = 0; j < 3; j = j + 1) {\n  print j;\n}";
        assertEquals(List.of("0", "1", "2"), out(src));
    }

    @DisplayName("for 루프 - 변수 누설 방지")
    @Test
    void forLoopVariableDoesNotLeak() {
        String src = "for (var j = 0; j < 1; j = j + 1) { print j; }\nprint j;";
        RunResult r = run(src);
        assertFalse(r.success());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message.contains("Undefined variable 'j'.")));
    }

    @DisplayName("주석 무시")
    @Test
    void commentsAreIgnored() {
        assertEquals(List.of("1"), out("// header comment\nprint 1; // trailing comment"));
    }

    @Test
    void logicalOperators() {
        assertEquals("true", single("print true or false;"));
        assertEquals("false", single("print true and false;"));
    }
}
