package codefab.assembler;

import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;

import java.util.ArrayList;
import java.util.List;

import static codefab.core.TokenType.*;

/**
 * Recursive-descent parser following the grammar in the README. Syntax errors
 * are recorded as diagnostics; the parser then synchronizes to a statement
 * boundary so a single mistake does not cascade.
 */
public final class Parser {
    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics;
    private int current = 0;

    public Parser(List<Token> tokens, List<Diagnostic> diagnostics) {
        this.tokens = tokens;
        this.diagnostics = diagnostics;
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            Stmt decl = declaration();
            if (decl != null) statements.add(decl);
        }
        return statements;
    }

    // --- declarations ------------------------------------------------------

    private Stmt declaration() {
        try {
            if (match(FUNC)) return functionDeclaration();
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt functionDeclaration() {
        int line = previous().line; // the 'Func' keyword
        Token name = consume(IDENTIFIER, "Expect function name.");
        consume(LEFT_PAREN, "Expect '(' after function name.");
        List<Token> params = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                params.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");
        consume(LEFT_BRACE, "Expect '{' before function body.");
        List<Stmt> body = block();
        return new Stmt.FunctionStmt(line, name, params, body);
    }

    private Stmt varDeclaration() {
        int line = previous().line; // the 'var' keyword
        Token name = consume(IDENTIFIER, "Expect variable name.");
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }
        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.VarStmt(line, name, initializer);
    }

    // --- statements --------------------------------------------------------

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(IF)) return ifStatement();
        if (match(FOR)) return forStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) {
            int line = previous().line;
            return new Stmt.BlockStmt(line, block());
        }
        return expressionStatement();
    }

    private Stmt printStatement() {
        int line = previous().line; // the 'print' keyword
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.PrintStmt(line, value);
    }

    private Stmt returnStatement() {
        Token keyword = previous(); // the 'return' keyword
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.ReturnStmt(keyword.line, keyword, value);
    }

    private Stmt ifStatement() {
        int line = previous().line; // the 'if' keyword
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.IfStmt(line, condition, thenBranch, elseBranch);
    }

    private Stmt forStatement() {
        int line = previous().line; // the 'for' keyword
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();
        return new Stmt.ForStmt(line, initializer, condition, increment, body);
    }

    private Stmt whileStatement() {
        int line = previous().line; // the 'while' keyword
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();
        return new Stmt.WhileStmt(line, condition, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            Stmt decl = declaration();
            if (decl != null) statements.add(decl);
        }
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt expressionStatement() {
        int line = peek().line; // first token of the expression
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.ExpressionStmt(line, expr);
    }

    // --- expressions -------------------------------------------------------

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or();
        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }
            // Report but do not throw: the LHS already parsed, so we can recover.
            error(equals, "Invalid assignment target.");
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();
        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();
        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS, PLUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    // call → primary ( "(" arguments? ")" )*
    private Expr call() {
        Expr expr = primary();
        while (match(LEFT_PAREN)) {
            expr = finishCall(expr);
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                arguments.add(expression());
            } while (match(COMMA));
        }
        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NUMBER, STRING)) return new Expr.Literal(previous().literal);
        if (match(IDENTIFIER)) return new Expr.Variable(previous());
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Expect expression.");
    }

    // --- token plumbing ----------------------------------------------------

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    // --- error handling ----------------------------------------------------

    private ParseError error(Token token, String message) {
        int line = token.line;
        String where = token.type == EOF ? " at end" : " at '" + token.lexeme + "'";
        diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER, line, message + where));
        return new ParseError();
    }

    /** Skip tokens until the likely start of the next statement. */
    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;
            switch (peek().type) {
                case FUNC:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
                default:
                    advance();
            }
        }
    }
}
