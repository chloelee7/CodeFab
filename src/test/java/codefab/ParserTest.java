package codefab;

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
import org.junit.jupiter.api.Test;

class ParserTest {

    @Test
    void print문과_var선언을_파싱한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("var a = 1 ; print a ;");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertEquals(2, stmts.size());
        assertInstanceOf(Stmt.VarStmt.class, stmts.get(0));
        assertInstanceOf(Stmt.PrintStmt.class, stmts.get(1));
    }

    @Test
    void 블록과_제어흐름을_파싱한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan(
            "if ( true ) { print 1 ; } else print 2 ; for ( ; ; ) print 3 ;");
        List<Stmt> stmts = new Parser(tokens, diags).parse();

        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        assertInstanceOf(Stmt.IfStmt.class, stmts.get(0));
        assertInstanceOf(Stmt.ForStmt.class, stmts.get(1));
    }

    @Test
    void 세미콜론_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("print 1 + 2");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains("Expect ';' after value.")));
    }

    @Test
    void 닫는_괄호_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("print ( 1 + 2 ;");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains("Expect ')' after expression.")));
    }

    @Test
    void 잘못된_대입_대상을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("var a = 1 ; var b = 2 ; a + b = 3 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains("Invalid assignment target.")));
    }

    @Test
    void 잘못된_표현식_시작을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("print * 5 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains("Expect expression.")));
    }

    @Test
    void 변수명_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("var = 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains("Expect variable name.")));
    }

    @Test
    void 변수_선언_세미콜론_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("var a = 1");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream()
            .anyMatch(d -> d.message.contains("Expect ';' after variable declaration.")));
    }

    @Test
    void if_여는_괄호_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("if true ) print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains("Expect '(' after 'if'.")));
    }

    @Test
    void if_닫는_괄호_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("if ( true print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains("Expect ')' after if condition.")));
    }

    @Test
    void for_여는_괄호_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("for ; ; ) print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains("Expect '(' after 'for'.")));
    }

    @Test
    void for_절_닫는_괄호_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("for ( ; ; 1");
        new Parser(tokens, diags).parse();

        assertTrue(
            diags.stream().anyMatch(d -> d.message.contains("Expect ')' after for clauses.")));
    }

    @Test
    void 블록_닫는_중괄호_누락을_보고한다() {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = mockScan("{ print 1 ;");
        new Parser(tokens, diags).parse();

        assertTrue(diags.stream().anyMatch(d -> d.message.contains("Expect '}' after block.")));
    }

    private static List<Token> mockScan(String source) {
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
