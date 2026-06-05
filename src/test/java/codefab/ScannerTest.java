package codefab;

import codefab.assembler.Scanner;
import codefab.core.Diagnostic;
import codefab.core.Token;
import codefab.core.TokenType;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("단일 문자 및 연산자 토큰을 스캔한다")
    void scansSingleCharacterAndOperatorTokens() {
        assertEquals(
                List.of(TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN, TokenType.LEFT_BRACE,
                        TokenType.RIGHT_BRACE, TokenType.PLUS, TokenType.MINUS, TokenType.STAR,
                        TokenType.SLASH, TokenType.SEMICOLON, TokenType.EOF),
                types("(){}+-*/;"));
    }

    @Test
    @DisplayName("1자·2자 토큰을 구분한다")
    void distinguishesOneAndTwoCharacterTokens() {
        assertEquals(
                List.of(TokenType.BANG, TokenType.BANG_EQUAL, TokenType.EQUAL, TokenType.EQUAL_EQUAL,
                        TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL,
                        TokenType.EOF),
                types("! != = == < <= > >="));
    }

    @Test
    @DisplayName("숫자를 double 타입으로 스캔한다")
    void scansNumbersAsDoubles() {
        List<Token> tokens = scan("3.14 5");
        assertEquals(TokenType.NUMBER, tokens.get(0).type);
        assertEquals(3.14, (Double) tokens.get(0).literal, 1e-9);
        assertEquals(5.0, (Double) tokens.get(1).literal, 1e-9);
    }

    @Test
    @DisplayName("문자열을 스캔한다")
    void scansStrings() {
        List<Token> tokens = scan("\"Hello, CodeFab!\"");
        assertEquals(TokenType.STRING, tokens.get(0).type);
        assertEquals("Hello, CodeFab!", tokens.get(0).literal);
    }

    @Test
    @DisplayName("키워드와 식별자를 구분한다")
    void distinguishesKeywordsFromIdentifiers() {
        assertEquals(
                List.of(TokenType.VAR, TokenType.IDENTIFIER, TokenType.PRINT, TokenType.IF,
                        TokenType.ELSE, TokenType.TRUE, TokenType.FALSE, TokenType.FOR,
                        TokenType.WHILE, TokenType.AND, TokenType.OR, TokenType.EOF),
                types("var foo print if else true false for while and or"));
    }

    @Test
    @DisplayName("신규 키워드(Func, return, nil, Array)를 올바른 토큰 타입으로 스캔한다")
    void scansNewKeywords() {
        assertEquals(
                List.of(TokenType.FUNC, TokenType.RETURN, TokenType.NIL, TokenType.ARRAY,
                        TokenType.EOF),
                types("Func return nil Array"));
    }

    @Test
    @DisplayName("% 연산자를 PERCENT 토큰으로 스캔한다")
    void scansPercentOperator() {
        assertEquals(
                List.of(TokenType.PERCENT, TokenType.EOF),
                types("%"));
    }

    @Test
    @DisplayName("대괄호([, ])를 올바른 토큰 타입으로 스캔한다")
    void scansBracketTokens() {
        assertEquals(
                List.of(TokenType.LEFT_BRACKET, TokenType.RIGHT_BRACKET, TokenType.EOF),
                types("[]"));
    }

    @Test
    @DisplayName("줄 주석과 공백을 무시한다")
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
    @DisplayName("줄 번호를 추적한다")
    void tracksLineNumbers() {
        List<Token> tokens = scan("1\n2\n3");
        assertEquals(1, tokens.get(0).line);
        assertEquals(2, tokens.get(1).line);
        assertEquals(3, tokens.get(2).line);
    }

    @Test
    @DisplayName("알 수 없는 문자를 에러로 보고한다")
    void reportsUnknownCharacter() {
        List<Diagnostic> diags = new ArrayList<>();
        new Scanner("@", diags).scanTokens();
        assertFalse(diags.isEmpty());
        assertEquals(Diagnostic.Stage.SCANNER, diags.get(0).stage);
    }

    @Test
    @DisplayName("종료되지 않은 문자열을 에러로 보고한다")
    void reportsUnterminatedString() {
        List<Diagnostic> diags = new ArrayList<>();
        new Scanner("\"oops", diags).scanTokens();
        assertFalse(diags.isEmpty());
        assertTrue(diags.get(0).message.toLowerCase().contains("string"));
    }
}
