package codefab.core;

import java.util.Objects;

/**
 * A lexical token. Tokens are not AST nodes; they are fields carried by AST
 * nodes. {@code literal} holds the runtime value for NUMBER/STRING tokens.
 */
public final class Token {
    public final TokenType type;
    public final String lexeme;
    public final Object literal;
    public final int line;

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    @Override
    public String toString() {
        return type + " '" + lexeme + "'" + (literal != null ? " " + literal : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token token = (Token) o;
        return line == token.line
                && type == token.type
                && lexeme.equals(token.lexeme)
                && Objects.equals(literal, token.literal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lexeme, literal, line);
    }
}
