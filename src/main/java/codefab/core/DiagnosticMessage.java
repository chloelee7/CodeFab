package codefab.core;

public final class DiagnosticMessage {

    private DiagnosticMessage() {
        // 상수 모음 유틸 클래스 — 인스턴스화 금지
    }

    // 변수 선언
    public static final String ERR_VARIABLE_NAME = "Expect variable name.";
    public static final String ERR_SEMICOLON_AFTER_VAR_DECL =
        "Expect ';' after variable declaration.";
    public static final String ERR_SEMICOLON_AFTER_VALUE = "Expect ';' after value.";

    // if / for / while
    public static final String ERR_LEFT_PAREN_AFTER_IF = "Expect '(' after 'if'.";
    public static final String ERR_RIGHT_PAREN_AFTER_IF_COND = "Expect ')' after if condition.";
    public static final String ERR_LEFT_PAREN_AFTER_FOR = "Expect '(' after 'for'.";
    public static final String ERR_LEFT_PAREN_AFTER_WHILE = "Expect '(' after 'while'.";
    public static final String ERR_RIGHT_PAREN_AFTER_CONDITION = "Expect ')' after condition.";
    public static final String ERR_SEMICOLON_AFTER_LOOP_COND = "Expect ';' after loop condition.";
    public static final String ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES = "Expect ')' after for clauses.";

    // 블록 / 그룹 / 일반
    public static final String ERR_RIGHT_BRACE_AFTER_BLOCK = "Expect '}' after block.";
    public static final String ERR_RIGHT_PAREN_AFTER_EXPR = "Expect ')' after expression.";
    public static final String ERR_INVALID_ASSIGN_TARGET = "Invalid assignment target.";
    public static final String ERR_EXPECT_EXPRESSION = "Expect expression.";

    // 함수 선언 (Func)
    public static final String ERR_FUNCTION_NAME = "Expected function name after 'Func'";
    public static final String ERR_LEFT_PAREN_AFTER_FUNC_NAME = "Expected '(' after function name";
    public static final String ERR_PARAMETER_NAME = "Expected parameter name";
    public static final String ERR_RIGHT_PAREN_AFTER_PARAMS = "Expected ')' after parameters";
    public static final String ERR_LEFT_BRACE_BEFORE_FUNC_BODY = "Expected '{' before function body";

    // return 문
    public static final String ERR_SEMICOLON_AFTER_RETURN = "Expected ';' after return value";

    // 배열
    public static final String ERR_RIGHT_BRACKET_AFTER_INDEX = "Expected ']' after index";
    public static final String ERR_RIGHT_PAREN_AFTER_ARGS = "Expected ')' after arguments";
}
