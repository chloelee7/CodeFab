package codefab;

import codefab.assembler.Scanner;
import codefab.core.Diagnostic;
import codefab.core.Token;
import codefab.core.TokenType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScannerTest {

    private List<Token> scan(String src) {
        List<Diagnostic> diags = new ArrayList<>();
        Scanner scanner = new Scanner(src, diags);
        List<Token> tokens = scanner.scanTokens();
        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        return tokens;
    }

    private List<TokenType> types(String src) {
        List<TokenType> result = new ArrayList<>();
        for (Token t : scan(src)) result.add(t.type);
        return result;
    }

    @Test
    void scansSingleCharacterAndOperatorTokens() {
        assertEquals(
                List.of(TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN, TokenType.LEFT_BRACE,
                        TokenType.RIGHT_BRACE, TokenType.PLUS, TokenType.MINUS, TokenType.STAR,
                        TokenType.SLASH, TokenType.SEMICOLON, TokenType.EOF),
                types("(){}+-*/;"));
    }

    @Test
    void distinguishesOneAndTwoCharacterTokens() {
        assertEquals(
                List.of(TokenType.BANG, TokenType.BANG_EQUAL, TokenType.EQUAL, TokenType.EQUAL_EQUAL,
                        TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL,
                        TokenType.EOF),
                types("! != = == < <= > >="));
    }

    @Test
    void scansNumbersAsDoubles() {
        List<Token> tokens = scan("3.14 5");
        assertEquals(TokenType.NUMBER, tokens.get(0).type);
        assertEquals(3.14, (Double) tokens.get(0).literal, 1e-9);
        assertEquals(5.0, (Double) tokens.get(1).literal, 1e-9);
    }

    @Test
    void scansStrings() {
        List<Token> tokens = scan("\"Hello, CodeFab!\"");
        assertEquals(TokenType.STRING, tokens.get(0).type);
        assertEquals("Hello, CodeFab!", tokens.get(0).literal);
    }

    @Test
    void distinguishesKeywordsFromIdentifiers() {
        assertEquals(
                List.of(TokenType.VAR, TokenType.IDENTIFIER, TokenType.PRINT, TokenType.IF,
                        TokenType.ELSE, TokenType.TRUE, TokenType.FALSE, TokenType.FOR,
                        TokenType.AND, TokenType.OR, TokenType.EOF),
                types("var foo print if else true false for and or"));
    }

    @Test
    void ignoresLineCommentsAndWhitespace() {
        List<Token> tokens = scan("var a = 1; // a comment\nprint a;");
        // first token after comment handling should still parse cleanly
        assertEquals(TokenType.VAR, tokens.get(0).type);
        // ensure the comment did not become tokens
        for (Token t : tokens) {
            assertNotEquals("//", t.lexeme);
        }
    }

    @Test
    void tracksLineNumbers() {
        List<Token> tokens = scan("1\n2\n3");
        assertEquals(1, tokens.get(0).line);
        assertEquals(2, tokens.get(1).line);
        assertEquals(3, tokens.get(2).line);
    }

    @Test
    void reportsUnknownCharacter() {
        List<Diagnostic> diags = new ArrayList<>();
        new Scanner("@", diags).scanTokens();
        assertFalse(diags.isEmpty());
        assertEquals(Diagnostic.Stage.SCANNER, diags.get(0).stage);
    }

    @Test
    void reportsUnterminatedString() {
        List<Diagnostic> diags = new ArrayList<>();
        new Scanner("\"oops", diags).scanTokens();
        assertFalse(diags.isEmpty());
        assertTrue(diags.get(0).message.toLowerCase().contains("string"));
    }
}
