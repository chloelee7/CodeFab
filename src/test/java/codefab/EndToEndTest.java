package codefab;

import static codefab.core.DiagnosticMessage.ERR_EXPECT_EXPRESSION;
import static codefab.core.DiagnosticMessage.ERR_INVALID_ASSIGN_TARGET;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_EXPR;
import static codefab.core.DiagnosticMessage.ERR_SEMICOLON_AFTER_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codefab.core.Diagnostic;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** End-to-end 테스트: 전체 파이프라인(scanner→parser→checker→executor)을 통과시킨다. */
class EndToEndTest {

    private static RunResult run(String src) {
        return new CodeFab().run(src);
    }

    /** 정상 프로그램: 캡처된 stdout을 검증한다. */
    @Nested
    @DisplayName("정상 동작")
    class 정상동작 {

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
        void comparisonAndBoolean() {
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
        void numberFormat() {
            assertEquals("5", single("print 5;"));
            assertEquals("5", single("print 5.0;"));
            assertEquals("3.14", single("print 3.14;"));
        }

        @DisplayName("변수 재할당")
        @Test
        void variableReassignment() {
            assertEquals(List.of("30"), out("var a = 10; var b = 20; print a + b;"));
            assertEquals(List.of("15"), out("var a = 10; a = a + 5; print a;"));
        }

        @DisplayName("블록 스코프 - 변수 섀도잉")
        @Test
        void blockScopeVariableShadowing() {
            String src = "var x = \"global\";\n{\n  var x = \"inner\";\n  print x;\n}\nprint x;";
            assertEquals(List.of("inner", "global"), out(src));
        }

        @DisplayName("블록 스코프 - 외부 변수 접근")
        @Test
        void accessOuterVariableFromBlock() {
            String src = "var count = 0;\n{\n  count = count + 1;\n}\nprint count;";
            assertEquals(List.of("1"), out(src));
        }

        @DisplayName("블록 스코프 - 중첩 섀도잉")
        @Test
        void nestedShadowingReadsOuterVariable() {
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

        @DisplayName("논리 연산자(and/or) 단락 평가")
        @Test
        void logicalOperatorShortCircuit() {
            assertEquals("true", single("print true or false;"));
            assertEquals("false", single("print true and false;"));
        }
    }

    private static void assertFailsAtStage(String src, Diagnostic.Stage stage, String substring) {
        RunResult r = run(src);
        assertFalse(r.success());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.stage == stage && d.message.contains(substring)),
                () -> "expected " + stage + " diagnostic containing '" + substring + "' but got: " + r.diagnostics());
    }

    /** 오류 프로그램: 의미있는 diagnostic과 함께 실패해야 한다. */
    @Nested
    @DisplayName("오류 처리")
    class 오류처리 {

        /** 구문 오류: PARSER 단계에서 실패한다. */
        @Nested
        @DisplayName("구문 오류 (PARSER)")
        class 구문오류 {

            @DisplayName("세미콜론 누락")
            @Test
            void missingSemicolon() {
                assertFailsAtStage("print 1 + 2", Diagnostic.Stage.PARSER, ERR_SEMICOLON_AFTER_VALUE);
            }

            @DisplayName("닫는 괄호 누락")
            @Test
            void missingClosingParen() {
                assertFailsAtStage("print (1 + 2;", Diagnostic.Stage.PARSER, ERR_RIGHT_PAREN_AFTER_EXPR);
            }

            @DisplayName("잘못된 대입 좌변")
            @Test
            void invalidAssignmentTarget() {
                assertFailsAtStage("var a = 1;\nvar b = 2;\na + b = 3;", Diagnostic.Stage.PARSER,
                        ERR_INVALID_ASSIGN_TARGET);
            }

            @DisplayName("잘못된 표현식 시작")
            @Test
            void invalidExpressionStart() {
                assertFailsAtStage("print * 5;", Diagnostic.Stage.PARSER, ERR_EXPECT_EXPRESSION);
            }
        }

        /** 검사기 오류: CHECKER 단계에서 실패하며 실행되지 않는다. */
        @Nested
        @DisplayName("검사기 오류 (CHECKER)")
        class 검사기오류 {

            @DisplayName("자기 초기화자에서 지역 변수 읽기")
            @Test
            void readLocalVarInOwnInitializer() {
                assertFailsAtStage("{\n  var a = a;\n}", Diagnostic.Stage.CHECKER,
                        "Can't read local variable in initializer.");
            }

            @DisplayName("같은 스코프 중복 선언")
            @Test
            void duplicateDeclarationInSameScope() {
                assertFailsAtStage("{\n  var a = \"hi\";\n  var a = 3;\n}", Diagnostic.Stage.CHECKER,
                        "Already a variable with this name in this scope.");
            }

            @DisplayName("검사기 오류는 실행을 막는다")
            @Test
            void checkerErrorPreventsExecution() {
                // checker가 먼저 실패하므로 print는 절대 실행되지 않는다.
                RunResult r = run("{\n  var a = a;\n  print \"reached\";\n}");
                assertFalse(r.success());
                assertTrue(r.output().isEmpty(), () -> "executor must not run: " + r.output());
            }
        }

        /** 런타임 오류: RUNTIME 단계에서 실패한다. */
        @Nested
        @DisplayName("런타임 오류 (RUNTIME)")
        class 런타임오류 {

            @DisplayName("정의되지 않은 변수")
            @Test
            void undefinedVariable() {
                assertFailsAtStage("print notDefined;", Diagnostic.Stage.RUNTIME,
                        "Undefined variable 'notDefined'.");
            }

            @DisplayName("정의되지 않은 변수에 대입")
            @Test
            void assignToUndefinedVariable() {
                assertFailsAtStage("undefinedVar = 1;", Diagnostic.Stage.RUNTIME,
                        "Undefined variable 'undefinedVar'.");
            }

            @DisplayName("타입이 섞인 덧셈")
            @Test
            void mixedTypeAddition() {
                assertFailsAtStage("print 1 + \"HI\";", Diagnostic.Stage.RUNTIME,
                        "Operands must be two numbers or two strings.");
            }

            @DisplayName("문자열 부호 반전")
            @Test
            void stringNegation() {
                assertFailsAtStage("print -\"FabCoding\";", Diagnostic.Stage.RUNTIME,
                        "Operand must be a number.");
            }

            @DisplayName("0으로 나누기")
            @Test
            void divisionByZero() {
                // 상수 폴딩은 0 나눗셈 시 폴딩만 생략하고 런타임에 위임
                assertFailsAtStage("print 3 / 0;", Diagnostic.Stage.RUNTIME, "Division by zero.");
            }

            @DisplayName("비교 연산은 숫자를 요구한다")
            @Test
            void comparisonRequiresNumbers() {
                assertFailsAtStage("print \"a\" < 1;", Diagnostic.Stage.RUNTIME, "Operands must be numbers.");
            }
        }
    }

    @Nested
    @DisplayName("함수 지원")
    class 함수지원 {

        private List<String> out(String src) {
            RunResult r = run(src);
            assertTrue(r.success(), () -> "expected success but got: " + r.diagnostics());
            return r.output();
        }

        @Test
        @DisplayName("함수를 선언하고 호출한다")
        void declaresAndCallsFunction() {
            List<String> result = out("Func greet() { print \"hello\"; } greet();");
            assertEquals(List.of("hello"), result);
        }

        @Test
        @DisplayName("매개변수를 전달하고 반환값을 출력한다")
        void passesParametersAndReturnsValue() {
            List<String> result = out("Func add(a, b) { return a + b; } print add(3, 4);");
            assertEquals(List.of("7"), result);
        }

        @Test
        @DisplayName("재귀 함수가 동작한다")
        void recursiveFunctionWorks() {
            String src = "Func fact(n) { if (n <= 1) { return 1; } return n * fact(n - 1); } print fact(5);";
            assertEquals(List.of("120"), out(src));
        }

        @Test
        @DisplayName("함수 외부에서 return은 체커 오류")
        void returnOutsideFunctionIsCheckerError() {
            assertFailsAtStage("return 1;", Diagnostic.Stage.CHECKER, "Can't return from top-level code.");
        }

        @Test
        @DisplayName("파라미터 이름 중복은 체커 오류")
        void duplicateParamIsCheckerError() {
            assertFailsAtStage("Func f(a, a) { print a; }", Diagnostic.Stage.CHECKER,
                    "Already a parameter with this name.");
        }
    }

    @Nested
    @DisplayName("배열 지원")
    class 배열지원 {

        private List<String> out(String src) {
            RunResult r = run(src);
            assertTrue(r.success(), () -> "expected success but got: " + r.diagnostics());
            return r.output();
        }

        @Test
        @DisplayName("배열을 생성하고 값을 쓰고 읽는다")
        void createsArrayAndReadsWrites() {
            List<String> result = out("var a = Array(3); a[0] = 10; a[1] = 20; print a[0] + a[1];");
            assertEquals(List.of("30"), result);
        }

        @Test
        @DisplayName("len(array)는 배열 길이를 반환하고 반복문 경계로 사용할 수 있다")
        void lenReturnsArrayLength() {
            String src = "var a = Array(3); a[0] = 2; a[1] = 4; a[2] = 6; "
                    + "var total = 0; "
                    + "for (var i = 0; i < len(a); i = i + 1) { total = total + a[i]; } "
                    + "print len(a); print total;";
            assertEquals(List.of("3", "12"), out(src));
        }

        @Test
        @DisplayName("배열 범위 초과는 런타임 오류")
        void outOfBoundsIsRuntimeError() {
            assertFailsAtStage("var a = Array(2); print a[5];", Diagnostic.Stage.RUNTIME,
                    "out of bounds");
        }

        @Test
        @DisplayName("비배열에 [] 접근은 런타임 오류")
        void indexingNonArrayIsRuntimeError() {
            assertFailsAtStage("var a = 3; print a[0];", Diagnostic.Stage.RUNTIME,
                    "Only arrays can be indexed.");
        }

        @Test
        @DisplayName("len()에 배열이 아닌 값을 넘기면 런타임 오류")
        void lenNonArrayIsRuntimeError() {
            assertFailsAtStage("print len(123);", Diagnostic.Stage.RUNTIME,
                    "len() expects an array.");
        }

        @Test
        @DisplayName("len() 인자 수가 맞지 않으면 런타임 오류")
        void lenArityMismatchIsRuntimeError() {
            assertFailsAtStage("print len();", Diagnostic.Stage.RUNTIME,
                    "len() takes exactly 1 argument.");
        }
    }

    @Nested
    @DisplayName("상수 폴딩 최적화")
    class 상수폴딩최적화 {

        private List<String> out(String src) {
            RunResult r = run(src);
            assertTrue(r.success(), () -> "expected success but got: " + r.diagnostics());
            return r.output();
        }

        @Test
        @DisplayName("리터럴 산술은 컴파일 타임에 계산된다")
        void literalArithmeticFolded() {
            assertEquals(List.of("10"), out("print 3 + 7;"));
            assertEquals(List.of("6"), out("print 2 * 3;"));
            assertEquals(List.of("5"), out("print 10 - 5;"));
        }

        @Test
        @DisplayName("중첩 상수 표현식도 폴딩된다")
        void nestedConstantsFolded() {
            assertEquals(List.of("14"), out("print 2 + 3 * 4;"));
        }

        @Test
        @DisplayName("변수가 포함된 표현식은 폴딩되지 않는다")
        void expressionWithVariableNotFolded() {
            assertEquals(List.of("8"), out("var x = 5; print x + 3;"));
        }
    }
}
