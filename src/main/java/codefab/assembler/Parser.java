package codefab.assembler;

import static codefab.core.TokenType.AND;
import static codefab.core.TokenType.BANG;
import static codefab.core.TokenType.BANG_EQUAL;
import static codefab.core.TokenType.ELSE;
import static codefab.core.TokenType.EOF;
import static codefab.core.TokenType.EQUAL;
import static codefab.core.TokenType.EQUAL_EQUAL;
import static codefab.core.TokenType.FALSE;
import static codefab.core.TokenType.FOR;
import static codefab.core.TokenType.GREATER;
import static codefab.core.TokenType.GREATER_EQUAL;
import static codefab.core.TokenType.IDENTIFIER;
import static codefab.core.TokenType.IF;
import static codefab.core.TokenType.LEFT_BRACE;
import static codefab.core.TokenType.LEFT_PAREN;
import static codefab.core.TokenType.LESS;
import static codefab.core.TokenType.LESS_EQUAL;
import static codefab.core.TokenType.MINUS;
import static codefab.core.TokenType.NUMBER;
import static codefab.core.TokenType.OR;
import static codefab.core.TokenType.PLUS;
import static codefab.core.TokenType.PRINT;
import static codefab.core.TokenType.RIGHT_BRACE;
import static codefab.core.TokenType.RIGHT_PAREN;
import static codefab.core.TokenType.SEMICOLON;
import static codefab.core.TokenType.SLASH;
import static codefab.core.TokenType.STAR;
import static codefab.core.TokenType.STRING;
import static codefab.core.TokenType.TRUE;
import static codefab.core.TokenType.VAR;

import codefab.core.Diagnostic;
import codefab.core.DiagnosticMessage;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
            if (decl != null) {
                statements.add(decl);
            }
        }
        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return varDeclaration();
            }
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, DiagnosticMessage.ERR_VARIABLE_NAME);
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }
        consume(SEMICOLON, DiagnosticMessage.ERR_SEMICOLON_AFTER_VAR_DECL);
        return new Stmt.VarStmt(name, initializer);
    }

    private Stmt statement() {
        if (match(PRINT)) {
            return printStatement();
        }
        if (match(IF)) {
            return ifStatement();
        }
        if (match(FOR)) {
            return forStatement();
        }
        if (match(LEFT_BRACE)) {
            return new Stmt.BlockStmt(block());
        }
        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, DiagnosticMessage.ERR_SEMICOLON_AFTER_VALUE);
        return new Stmt.PrintStmt(value);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, DiagnosticMessage.ERR_LEFT_PAREN_AFTER_IF);
        Expr condition = expression();
        consume(RIGHT_PAREN, DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_IF_COND);

        Stmt thenBranch = statement();
        Stmt elseBranch = match(ELSE) ? statement() : null;
        return new Stmt.IfStmt(condition, thenBranch, elseBranch);
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, DiagnosticMessage.ERR_LEFT_PAREN_AFTER_FOR);

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = check(SEMICOLON) ? null : expression();
        consume(SEMICOLON, DiagnosticMessage.ERR_SEMICOLON_AFTER_LOOP_COND);

        Expr increment = check(RIGHT_PAREN) ? null : expression();
        consume(RIGHT_PAREN, DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES);

        Stmt body = statement();
        return new Stmt.ForStmt(initializer, condition, increment, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            Stmt decl = declaration();
            if (decl != null) {
                statements.add(decl);
            }
        }
        consume(RIGHT_BRACE, DiagnosticMessage.ERR_RIGHT_BRACE_AFTER_BLOCK);
        return statements;
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, DiagnosticMessage.ERR_SEMICOLON_AFTER_VALUE);
        return new Stmt.ExpressionStmt(expr);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or();
        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();
            if (expr instanceof Expr.Variable) {
                return new Expr.Assign(((Expr.Variable) expr).name, value);
            }
            error(equals, DiagnosticMessage.ERR_INVALID_ASSIGN_TARGET);
        }
        return expr;
    }

    private Expr or() {
        return leftAssocLogical(this::and, OR);
    }

    private Expr and() {
        return leftAssocLogical(this::equality, AND);
    }

    private Expr equality() {
        return leftAssocBinary(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
    }

    private Expr comparison() {
        return leftAssocBinary(this::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
    }

    private Expr term() {
        return leftAssocBinary(this::factor, MINUS, PLUS);
    }

    private Expr factor() {
        return leftAssocBinary(this::unary, SLASH, STAR);
    }

    private Expr unary() {
        if (match(BANG, MINUS, PLUS)) {
            Token operator = previous();
            return new Expr.Unary(operator, unary());
        }
        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) {
            return new Expr.Literal(false);
        }
        if (match(TRUE)) {
            return new Expr.Literal(true);
        }
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }
        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_EXPR);
            return new Expr.Grouping(expr);
        }
        throw error(peek(), DiagnosticMessage.ERR_EXPECT_EXPRESSION);
    }

    private Expr leftAssocBinary(Supplier<Expr> operand, TokenType... operators) {
        Expr expr = operand.get();
        while (match(operators)) {
            Token operator = previous();
            Expr right = operand.get();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr leftAssocLogical(Supplier<Expr> operand, TokenType operator) {
        Expr expr = operand.get();
        while (match(operator)) {
            Token op = previous();
            Expr right = operand.get();
            expr = new Expr.Logical(expr, op, right);
        }
        return expr;
    }

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
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
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

    private ParseError error(Token token, String message) {
        String where = token.type == EOF ? " at end" : " at '" + token.lexeme + "'";
        diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER, token.line, message + where));
        return new ParseError();
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return;
            }
            switch (peek().type) {
                case VAR:
                case FOR:
                case IF:
                case PRINT:
                    return;
                default:
                    advance();
            }
        }
    }
}
