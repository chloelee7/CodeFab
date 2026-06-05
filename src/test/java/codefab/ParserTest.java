package codefab;

import static codefab.core.DiagnosticMessage.ERR_EXPECT_EXPRESSION;
import static codefab.core.DiagnosticMessage.ERR_INVALID_ASSIGN_TARGET;
import static codefab.core.DiagnosticMessage.ERR_LEFT_PAREN_AFTER_FOR;
import static codefab.core.DiagnosticMessage.ERR_LEFT_PAREN_AFTER_IF;
import static codefab.core.DiagnosticMessage.ERR_LEFT_PAREN_AFTER_WHILE;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_BRACE_AFTER_BLOCK;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_CONDITION;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_EXPR;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_IF_COND;
import static codefab.core.DiagnosticMessage.ERR_SEMICOLON_AFTER_VALUE;
import static codefab.core.DiagnosticMessage.ERR_SEMICOLON_AFTER_VAR_DECL;
import static codefab.core.DiagnosticMessage.ERR_VARIABLE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codefab.assembler.Parser;
import codefab.core.Diagnostic;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParserTest {

    List<Diagnostic> diags;

    @BeforeEach
    void setUp() {
        diags = new ArrayList<>();
    }

    @DisplayName("var 선언문과 print 문을 올바르게 파싱한다")
    @Test
    void varAndPrintStatementsAreParsedCorrectly() {
        List<Token> tokens = tokensOf("var a = 1 ; print a ;");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertEquals(2, stmts.size());
        assertInstanceOf(Stmt.VarStmt.class, stmts.get(0));
        assertInstanceOf(Stmt.PrintStmt.class, stmts.get(1));
    }

    @DisplayName("if/else 문과 for 문을 올바르게 파싱한다")
    @Test
    void ifElseAndForStatementsAreParsedCorrectly() {
        List<Token> tokens = tokensOf(
            "if ( true ) { print 1 ; } else print 2 ; for ( ; ; ) print 3 ;");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertInstanceOf(Stmt.IfStmt.class, stmts.get(0));
        assertInstanceOf(Stmt.ForStmt.class, stmts.get(1));
    }

    @DisplayName("print 문 값 뒤에 세미콜론이 없으면 에러를 보고한다")
    @Test
    void missingTerminatingSemicolonInPrintReportsError() {
        List<Token> tokens = tokensOf("print 1 + 2");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_SEMICOLON_AFTER_VALUE)));
    }

    @DisplayName("그룹화 표현식에 닫는 괄호가 없으면 에러를 보고한다")
    @Test
    void missingClosingParenInGroupingReportsError() {
        List<Token> tokens = tokensOf("print ( 1 + 2 ;");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_PAREN_AFTER_EXPR)));
    }

    @DisplayName("대입 좌변이 변수가 아니면 에러를 보고한다")
    @Test
    void nonVariableAssignmentTargetReportsError() {
        List<Token> tokens = tokensOf("var a = 1 ; var b = 2 ; a + b = 3 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_INVALID_ASSIGN_TARGET)));
    }

    @DisplayName("표현식이 이항 연산자로 시작하면 에러를 보고한다")
    @Test
    void expressionStartingWithBinaryOperatorReportsError() {
        List<Token> tokens = tokensOf("print * 5 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_EXPECT_EXPRESSION)));
    }

    @DisplayName("var 뒤에 변수명이 없으면 에러를 보고한다")
    @Test
    void missingVariableNameAfterVarReportsError() {
        List<Token> tokens = tokensOf("var = 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_VARIABLE_NAME)));
    }

    @DisplayName("var 선언 끝에 세미콜론이 없으면 에러를 보고한다")
    @Test
    void missingSemicolonAfterVarDeclReportsError() {
        List<Token> tokens = tokensOf("var a = 1");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream()
            .anyMatch(d -> d.message.contains(ERR_SEMICOLON_AFTER_VAR_DECL)));
    }

    @DisplayName("if 조건 앞에 여는 괄호가 없으면 에러를 보고한다")
    @Test
    void missingOpenParenBeforeIfCondReportsError() {
        List<Token> tokens = tokensOf("if true ) print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_LEFT_PAREN_AFTER_IF)));
    }

    @DisplayName("if 조건 뒤에 닫는 괄호가 없으면 에러를 보고한다")
    @Test
    void missingCloseParenAfterIfCondReportsError() {
        List<Token> tokens = tokensOf("if ( true print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_PAREN_AFTER_IF_COND)));
    }

    @DisplayName("for 절 앞에 여는 괄호가 없으면 에러를 보고한다")
    @Test
    void missingOpenParenBeforeForClausesReportsError() {
        List<Token> tokens = tokensOf("for ; ; ) print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_LEFT_PAREN_AFTER_FOR)));
    }

    @DisplayName("for 절 뒤에 닫는 괄호가 없으면 에러를 보고한다")
    @Test
    void missingCloseParenAfterForClausesReportsError() {
        List<Token> tokens = tokensOf("for ( ; ; 1");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES)));
    }

    @DisplayName("while 문을 WhileStmt로 파싱한다")
    @Test
    void whileStatementIsParsedCorrectly() {
        List<Token> tokens = tokensOf("while ( true ) print 1 ;");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertEquals(1, stmts.size());
        assertInstanceOf(Stmt.WhileStmt.class, stmts.get(0));
    }

    @DisplayName("while 조건 앞에 여는 괄호가 없으면 에러를 보고한다")
    @Test
    void missingOpenParenBeforeWhileCondReportsError() {
        List<Token> tokens = tokensOf("while true ) print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_LEFT_PAREN_AFTER_WHILE)));
    }

    @DisplayName("while 조건 뒤에 닫는 괄호가 없으면 에러를 보고한다")
    @Test
    void missingCloseParenAfterWhileCondReportsError() {
        List<Token> tokens = tokensOf("while ( true print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_PAREN_AFTER_CONDITION)));
    }

    @DisplayName("블록 끝에 닫는 중괄호가 없으면 에러를 보고한다")
    @Test
    void missingClosingBraceAfterBlockReportsError() {
        List<Token> tokens = tokensOf("{ print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_BRACE_AFTER_BLOCK)));
    }

    @DisplayName("함수 선언문을 FunctionStmt로 파싱한다")
    @Test
    void functionDeclarationIsParsedCorrectly() {
        List<Token> tokens = tokensOf("Func foo ( ) { print 1 ; }");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertEquals(1, stmts.size());
        assertInstanceOf(Stmt.FunctionStmt.class, stmts.get(0));
        Stmt.FunctionStmt fn = (Stmt.FunctionStmt) stmts.get(0);
        assertEquals("foo", fn.name.lexeme);
        assertEquals(0, fn.params.size());
    }

    @DisplayName("매개변수가 있는 함수 선언문을 파싱한다")
    @Test
    void functionDeclarationWithParamsIsParsedCorrectly() {
        List<Token> tokens = tokensOf("Func add ( a , b ) { print a ; }");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertInstanceOf(Stmt.FunctionStmt.class, stmts.get(0));
        Stmt.FunctionStmt fn = (Stmt.FunctionStmt) stmts.get(0);
        assertEquals(2, fn.params.size());
        assertEquals("a", fn.params.get(0).lexeme);
        assertEquals("b", fn.params.get(1).lexeme);
    }

    @DisplayName("return 문을 ReturnStmt로 파싱한다")
    @Test
    void returnStatementIsParsedCorrectly() {
        List<Token> tokens = tokensOf("Func f ( ) { return 1 ; }");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        Stmt.FunctionStmt fn = (Stmt.FunctionStmt) stmts.get(0);
        assertEquals(1, fn.body.size());
        assertInstanceOf(Stmt.ReturnStmt.class, fn.body.get(0));
    }

    @DisplayName("Func 키워드 뒤에 이름이 없으면 에러를 보고한다")
    @Test
    void missingFunctionNameAfterFuncReportsError() {
        List<Token> tokens = tokensOf("Func ( ) { }");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(
            codefab.core.DiagnosticMessage.ERR_FUNCTION_NAME)));
    }

    @DisplayName("함수 파라미터 목록에 닫는 괄호가 없으면 에러를 보고한다")
    @Test
    void missingCloseParenAfterParamsReportsError() {
        List<Token> tokens = tokensOf("Func f ( a { }");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(
            codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_PARAMS)));
    }

    @DisplayName("배열 인덱싱 표현식(arr[i])을 파싱한다")
    @Test
    void arrayIndexingIsParsedCorrectly() {
        List<Token> tokens = tokensOf("print arr [ 0 ] ;");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertEquals(1, stmts.size());
        assertInstanceOf(Stmt.PrintStmt.class, stmts.get(0));
        assertInstanceOf(codefab.core.Expr.ArrayGet.class,
            ((Stmt.PrintStmt) stmts.get(0)).expression);
    }

    private static List<Token> tokensOf(String source) {
        List<Token> tokens = new ArrayList<>();
        for (String lexeme : source.split(" ")) {
            if (!lexeme.isEmpty()) {
                tokens.add(tokenOf(lexeme));
            }
        }
        tokens.add(new Token(TokenType.EOF, "", null, 1));
        return tokens;
    }

    private static Token tokenOf(String lexeme) {
        switch (lexeme) {
            case "var":
                return new Token(TokenType.VAR, lexeme, null, 1);
            case "print":
                return new Token(TokenType.PRINT, lexeme, null, 1);
            case "if":
                return new Token(TokenType.IF, lexeme, null, 1);
            case "else":
                return new Token(TokenType.ELSE, lexeme, null, 1);
            case "for":
                return new Token(TokenType.FOR, lexeme, null, 1);
            case "while":
                return new Token(TokenType.WHILE, lexeme, null, 1);
            case "true":
                return new Token(TokenType.TRUE, lexeme, null, 1);
            case "false":
                return new Token(TokenType.FALSE, lexeme, null, 1);
            case "=":
                return new Token(TokenType.EQUAL, lexeme, null, 1);
            case ";":
                return new Token(TokenType.SEMICOLON, lexeme, null, 1);
            case "+":
                return new Token(TokenType.PLUS, lexeme, null, 1);
            case "*":
                return new Token(TokenType.STAR, lexeme, null, 1);
            case "(":
                return new Token(TokenType.LEFT_PAREN, lexeme, null, 1);
            case ")":
                return new Token(TokenType.RIGHT_PAREN, lexeme, null, 1);
            case "{":
                return new Token(TokenType.LEFT_BRACE, lexeme, null, 1);
            case "}":
                return new Token(TokenType.RIGHT_BRACE, lexeme, null, 1);
            case "Func":
                return new Token(TokenType.FUNC, lexeme, null, 1);
            case "return":
                return new Token(TokenType.RETURN, lexeme, null, 1);
            case "nil":
                return new Token(TokenType.NIL, lexeme, null, 1);
            case "Array":
                return new Token(TokenType.ARRAY, lexeme, null, 1);
            case ",":
                return new Token(TokenType.COMMA, lexeme, null, 1);
            case "[":
                return new Token(TokenType.LEFT_BRACKET, lexeme, null, 1);
            case "]":
                return new Token(TokenType.RIGHT_BRACKET, lexeme, null, 1);
            default:
                if (lexeme.matches("\\d+")) {
                    return new Token(TokenType.NUMBER, lexeme, Double.parseDouble(lexeme), 1);
                }
                return new Token(TokenType.IDENTIFIER, lexeme, null, 1);
        }
    }
}
