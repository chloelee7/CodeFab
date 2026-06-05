package codefab.core;

/**
 * All token categories the Scanner can produce. Grouped by single-character, one-or-two-character,
 * literals and keywords.
 */
public enum TokenType {
    // Single-character tokens.
    LEFT_PAREN, RIGHT_PAREN,
    LEFT_BRACE, RIGHT_BRACE,
    LEFT_BRACKET, RIGHT_BRACKET,
    COMMA, DOT, SEMICOLON,
    PLUS, MINUS, STAR, SLASH, PERCENT,

    // One or two character tokens.
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // Literals.
    IDENTIFIER, STRING, NUMBER,

    // Keywords.
    AND, OR,
    IF, ELSE, TRUE, FALSE, NIL, FOR, WHILE, VAR, PRINT,
    FUNC, RETURN, ARRAY,

    EOF
}
