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
        while (!reachedEof()) {
            Stmt decl = declaration();
            if (decl != null) {
                statements.add(decl);
            }
        }
        return statements;
    }

    private Stmt declaration() {
        try {
            if (matchAndAdvance(VAR)) {
                return varDeclaration();
            }
            return statement();
        } catch (ParseError error) {
            recoverToNextStatement();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = expectAndConsume(IDENTIFIER, DiagnosticMessage.ERR_VARIABLE_NAME);
        Expr initializer = null;
        if (matchAndAdvance(EQUAL)) {
            initializer = expression();
        }
        expectAndConsume(SEMICOLON, DiagnosticMessage.ERR_SEMICOLON_AFTER_VAR_DECL);
        return new Stmt.VarStmt(name, initializer);
    }

    private Stmt statement() {
        if (matchAndAdvance(PRINT)) {
            return printStatement();
        }
        if (matchAndAdvance(IF)) {
            return ifStatement();
        }
        if (matchAndAdvance(FOR)) {
            return forStatement();
        }
        if (matchAndAdvance(LEFT_BRACE)) {
            return new Stmt.BlockStmt(block());
        }
        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        expectAndConsume(SEMICOLON, DiagnosticMessage.ERR_SEMICOLON_AFTER_VALUE);
        return new Stmt.PrintStmt(value);
    }

    private Stmt ifStatement() {
        expectAndConsume(LEFT_PAREN, DiagnosticMessage.ERR_LEFT_PAREN_AFTER_IF);
        Expr condition = expression();
        expectAndConsume(RIGHT_PAREN, DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_IF_COND);

        Stmt thenBranch = statement();
        Stmt elseBranch = matchAndAdvance(ELSE) ? statement() : null;
        return new Stmt.IfStmt(condition, thenBranch, elseBranch);
    }

    private Stmt forStatement() {
        expectAndConsume(LEFT_PAREN, DiagnosticMessage.ERR_LEFT_PAREN_AFTER_FOR);

        Stmt initializer;
        if (matchAndAdvance(SEMICOLON)) {
            initializer = null;
        } else if (matchAndAdvance(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = currentTokenIs(SEMICOLON) ? null : expression();
        expectAndConsume(SEMICOLON, DiagnosticMessage.ERR_SEMICOLON_AFTER_LOOP_COND);

        Expr increment = currentTokenIs(RIGHT_PAREN) ? null : expression();
        expectAndConsume(RIGHT_PAREN, DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES);

        Stmt body = statement();
        return new Stmt.ForStmt(initializer, condition, increment, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!currentTokenIs(RIGHT_BRACE) && !reachedEof()) {
            Stmt decl = declaration();
            if (decl != null) {
                statements.add(decl);
            }
        }
        expectAndConsume(RIGHT_BRACE, DiagnosticMessage.ERR_RIGHT_BRACE_AFTER_BLOCK);
        return statements;
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        expectAndConsume(SEMICOLON, DiagnosticMessage.ERR_SEMICOLON_AFTER_VALUE);
        return new Stmt.ExpressionStmt(expr);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or();
        if (matchAndAdvance(EQUAL)) {
            Token equals = previousToken();
            Expr value = assignment();
            if (expr instanceof Expr.Variable) {
                return new Expr.Assign(((Expr.Variable) expr).name, value);
            }
            reportError(equals, DiagnosticMessage.ERR_INVALID_ASSIGN_TARGET);
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
        if (matchAndAdvance(BANG, MINUS, PLUS)) {
            Token operator = previousToken();
            return new Expr.Unary(operator, unary());
        }
        return primary();
    }

    private Expr primary() {
        if (matchAndAdvance(FALSE)) {
            return new Expr.Literal(false);
        }
        if (matchAndAdvance(TRUE)) {
            return new Expr.Literal(true);
        }
        if (matchAndAdvance(NUMBER, STRING)) {
            return new Expr.Literal(previousToken().literal);
        }
        if (matchAndAdvance(IDENTIFIER)) {
            return new Expr.Variable(previousToken());
        }
        if (matchAndAdvance(LEFT_PAREN)) {
            Expr expr = expression();
            expectAndConsume(RIGHT_PAREN, DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_EXPR);
            return new Expr.Grouping(expr);
        }
        throw reportError(currentToken(), DiagnosticMessage.ERR_EXPECT_EXPRESSION);
    }

    private Expr leftAssocBinary(Supplier<Expr> operand, TokenType... operators) {
        Expr expr = operand.get();
        while (matchAndAdvance(operators)) {
            Token operator = previousToken();
            Expr right = operand.get();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr leftAssocLogical(Supplier<Expr> operand, TokenType operator) {
        Expr expr = operand.get();
        while (matchAndAdvance(operator)) {
            Token op = previousToken();
            Expr right = operand.get();
            expr = new Expr.Logical(expr, op, right);
        }
        return expr;
    }

    private boolean matchAndAdvance(TokenType... types) {
        for (TokenType type : types) {
            if (currentTokenIs(type)) {
                advanceCursor();
                return true;
            }
        }
        return false;
    }

    private Token expectAndConsume(TokenType type, String message) {
        if (currentTokenIs(type)) {
            return advanceCursor();
        }
        throw reportError(currentToken(), message);
    }

    private boolean currentTokenIs(TokenType type) {
        return !reachedEof() && currentToken().type == type;
    }

    private Token advanceCursor() {
        if (!reachedEof()) {
            current++;
        }
        return previousToken();
    }

    private boolean reachedEof() {
        return currentToken().type == EOF;
    }

    private Token currentToken() {
        return tokens.get(current);
    }

    private Token previousToken() {
        return tokens.get(current - 1);
    }

    private ParseError reportError(Token token, String message) {
        String where = token.type == EOF ? " at end" : " at '" + token.lexeme + "'";
        diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER, token.line, message + where));
        return new ParseError();
    }

    private void recoverToNextStatement() {
        advanceCursor();
        while (!reachedEof()) {
            if (previousToken().type == SEMICOLON) {
                return;
            }
            switch (currentToken().type) {
                case VAR:
                case FOR:
                case IF:
                case PRINT:
                    return;
                default:
                    advanceCursor();
            }
        }
    }
}
