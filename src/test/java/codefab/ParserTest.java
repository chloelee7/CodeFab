package codefab;

import static codefab.core.DiagnosticMessage.ERR_EXPECT_EXPRESSION;
import static codefab.core.DiagnosticMessage.ERR_INVALID_ASSIGN_TARGET;
import static codefab.core.DiagnosticMessage.ERR_LEFT_PAREN_AFTER_FOR;
import static codefab.core.DiagnosticMessage.ERR_LEFT_PAREN_AFTER_IF;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_BRACE_AFTER_BLOCK;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_EXPR;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_IF_COND;
import static codefab.core.DiagnosticMessage.ERR_SEMICOLON_AFTER_VALUE;
import static codefab.core.DiagnosticMessage.ERR_SEMICOLON_AFTER_VAR_DECL;
import static codefab.core.DiagnosticMessage.ERR_VARIABLE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.core.Diagnostic;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParserTest {

    List<Diagnostic> diags;

    @BeforeEach
    void setUp() {
        diags = new ArrayList<>();
    }

    @Test
    void print문과_var선언을_파싱한다() {
        List<Token> tokens = mockScanner("var a = 1 ; print a ;");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertEquals(2, stmts.size());
        assertInstanceOf(Stmt.VarStmt.class, stmts.get(0));
        assertInstanceOf(Stmt.PrintStmt.class, stmts.get(1));
    }

    @Test
    void 블록과_제어흐름을_파싱한다() {
        List<Token> tokens = mockScanner(
            "if ( true ) { print 1 ; } else print 2 ; for ( ; ; ) print 3 ;");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertInstanceOf(Stmt.IfStmt.class, stmts.get(0));
        assertInstanceOf(Stmt.ForStmt.class, stmts.get(1));
    }

    @Test
    void 세미콜론_누락을_보고한다() {
        List<Token> tokens = mockScanner("print 1 + 2");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_SEMICOLON_AFTER_VALUE)));
    }

    @Test
    void 닫는_괄호_누락을_보고한다() {
        List<Token> tokens = mockScanner("print ( 1 + 2 ;");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_PAREN_AFTER_EXPR)));
    }

    @Test
    void 잘못된_대입_대상을_보고한다() {
        List<Token> tokens = mockScanner("var a = 1 ; var b = 2 ; a + b = 3 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_INVALID_ASSIGN_TARGET)));
    }

    @Test
    void 잘못된_표현식_시작을_보고한다() {
        List<Token> tokens = mockScanner("print * 5 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_EXPECT_EXPRESSION)));
    }

    @Test
    void 변수명_누락을_보고한다() {
        List<Token> tokens = mockScanner("var = 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_VARIABLE_NAME)));
    }

    @Test
    void 변수_선언_세미콜론_누락을_보고한다() {
        List<Token> tokens = mockScanner("var a = 1");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream()
            .anyMatch(d -> d.message.contains(ERR_SEMICOLON_AFTER_VAR_DECL)));
    }

    @Test
    void if_여는_괄호_누락을_보고한다() {
        List<Token> tokens = mockScanner("if true ) print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_LEFT_PAREN_AFTER_IF)));
    }

    @Test
    void if_닫는_괄호_누락을_보고한다() {
        List<Token> tokens = mockScanner("if ( true print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_PAREN_AFTER_IF_COND)));
    }

    @Test
    void for_여는_괄호_누락을_보고한다() {
        List<Token> tokens = mockScanner("for ; ; ) print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_LEFT_PAREN_AFTER_FOR)));
    }

    @Test
    void for_절_닫는_괄호_누락을_보고한다() {
        List<Token> tokens = mockScanner("for ( ; ; 1");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES)));
    }

    @Test
    void 블록_닫는_중괄호_누락을_보고한다() {
        List<Token> tokens = mockScanner("{ print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains(ERR_RIGHT_BRACE_AFTER_BLOCK)));
    }

    private static List<Token> mockScanner(String source) {
        List<Token> tokens = new ArrayList<>();
        for (String lexeme : source.split(" ")) {
            if (!lexeme.isEmpty()) {
                tokens.add(tokenOf(lexeme));
            }
        }
        tokens.add(new Token(TokenType.EOF, "", null, 1));

        Scanner scanner = mock(Scanner.class);
        when(scanner.scanTokens()).thenReturn(tokens);
        return scanner.scanTokens();
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
            default:
                if (lexeme.matches("\\d+")) {
                    return new Token(TokenType.NUMBER, lexeme, Double.parseDouble(lexeme), 1);
                }
                return new Token(TokenType.IDENTIFIER, lexeme, null, 1);
        }
    }
}
