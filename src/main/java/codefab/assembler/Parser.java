package codefab.assembler;

import static codefab.core.DiagnosticMessage.ERR_EXPECT_EXPRESSION;
import static codefab.core.DiagnosticMessage.ERR_INVALID_ASSIGN_TARGET;
import static codefab.core.DiagnosticMessage.ERR_LEFT_PAREN_AFTER_FOR;
import static codefab.core.DiagnosticMessage.ERR_LEFT_PAREN_AFTER_IF;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_BRACE_AFTER_BLOCK;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_EXPR;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES;
import static codefab.core.DiagnosticMessage.ERR_RIGHT_PAREN_AFTER_IF_COND;
import static codefab.core.DiagnosticMessage.ERR_SEMICOLON_AFTER_LOOP_COND;
import static codefab.core.DiagnosticMessage.ERR_SEMICOLON_AFTER_VALUE;
import static codefab.core.DiagnosticMessage.ERR_SEMICOLON_AFTER_VAR_DECL;
import static codefab.core.DiagnosticMessage.ERR_VARIABLE_NAME;

import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import java.util.ArrayList;
import java.util.List;

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
        while (tokens.get(current).type != TokenType.EOF) {
            Stmt decl = declaration();
            if (decl != null) {
                statements.add(decl);
            }
        }
        return statements;
    }

    private Stmt declaration() {
        try {
            if (tokens.get(current).type == TokenType.VAR) {
                if (tokens.get(current).type != TokenType.EOF) {
                    current++;
                }
                return varDeclaration();
            }
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        if (tokens.get(current).type != TokenType.IDENTIFIER) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_VARIABLE_NAME + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }
        Token name = tokens.get(current - 1);

        Expr initializer = null;
        if (tokens.get(current).type == TokenType.EQUAL) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            initializer = expression();
        }

        if (tokens.get(current).type != TokenType.SEMICOLON) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_SEMICOLON_AFTER_VAR_DECL + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }
        return new Stmt.VarStmt(name, initializer);
    }

    private Stmt statement() {
        if (tokens.get(current).type == TokenType.PRINT) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return printStatement();
        }
        if (tokens.get(current).type == TokenType.IF) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return ifStatement();
        }
        if (tokens.get(current).type == TokenType.FOR) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return forStatement();
        }
        if (tokens.get(current).type == TokenType.LEFT_BRACE) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return new Stmt.BlockStmt(block());
        }
        return expressionStatement();
    }

    private Stmt printStatement() {
        Expr value = expression();
        if (tokens.get(current).type != TokenType.SEMICOLON) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_SEMICOLON_AFTER_VALUE + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }
        return new Stmt.PrintStmt(value);
    }

    private Stmt ifStatement() {
        if (tokens.get(current).type != TokenType.LEFT_PAREN) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_LEFT_PAREN_AFTER_IF + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }

        Expr condition = expression();

        if (tokens.get(current).type != TokenType.RIGHT_PAREN) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_RIGHT_PAREN_AFTER_IF_COND + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (tokens.get(current).type == TokenType.ELSE) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            elseBranch = statement();
        }
        return new Stmt.IfStmt(condition, thenBranch, elseBranch);
    }

    private Stmt forStatement() {
        if (tokens.get(current).type != TokenType.LEFT_PAREN) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_LEFT_PAREN_AFTER_FOR + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }

        Stmt initializer;
        if (tokens.get(current).type == TokenType.SEMICOLON) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            initializer = null;
        } else if (tokens.get(current).type == TokenType.VAR) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (tokens.get(current).type != TokenType.SEMICOLON) {
            condition = expression();
        }
        if (tokens.get(current).type != TokenType.SEMICOLON) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_SEMICOLON_AFTER_LOOP_COND + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }

        Expr increment = null;
        if (tokens.get(current).type != TokenType.RIGHT_PAREN) {
            increment = expression();
        }
        if (tokens.get(current).type != TokenType.RIGHT_PAREN) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_RIGHT_PAREN_AFTER_FOR_CLAUSES + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }

        Stmt body = statement();
        return new Stmt.ForStmt(initializer, condition, increment, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (tokens.get(current).type != TokenType.RIGHT_BRACE
            && tokens.get(current).type != TokenType.EOF) {
            Stmt decl = declaration();
            if (decl != null) {
                statements.add(decl);
            }
        }
        if (tokens.get(current).type != TokenType.RIGHT_BRACE) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_RIGHT_BRACE_AFTER_BLOCK + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }
        return statements;
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        if (tokens.get(current).type != TokenType.SEMICOLON) {
            String where;
            if (tokens.get(current).type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + tokens.get(current).lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                tokens.get(current).line, ERR_SEMICOLON_AFTER_VALUE + where));
            throw new ParseError();
        }
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }
        return new Stmt.ExpressionStmt(expr);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or();
        if (tokens.get(current).type == TokenType.EQUAL) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Token equals = tokens.get(current - 1);
            Expr value = assignment();
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }
            String where;
            if (equals.type == TokenType.EOF) {
                where = " at end";
            } else {
                where = " at '" + equals.lexeme + "'";
            }
            diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                equals.line, ERR_INVALID_ASSIGN_TARGET + where));
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();
        while (tokens.get(current).type == TokenType.OR) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Token operator = tokens.get(current - 1);
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();
        while (tokens.get(current).type == TokenType.AND) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Token operator = tokens.get(current - 1);
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (tokens.get(current).type == TokenType.BANG_EQUAL
            || tokens.get(current).type == TokenType.EQUAL_EQUAL) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Token operator = tokens.get(current - 1);
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (tokens.get(current).type == TokenType.GREATER
            || tokens.get(current).type == TokenType.GREATER_EQUAL
            || tokens.get(current).type == TokenType.LESS
            || tokens.get(current).type == TokenType.LESS_EQUAL) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Token operator = tokens.get(current - 1);
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (tokens.get(current).type == TokenType.MINUS
            || tokens.get(current).type == TokenType.PLUS) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Token operator = tokens.get(current - 1);
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (tokens.get(current).type == TokenType.SLASH
            || tokens.get(current).type == TokenType.STAR) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Token operator = tokens.get(current - 1);
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (tokens.get(current).type == TokenType.BANG
            || tokens.get(current).type == TokenType.MINUS
            || tokens.get(current).type == TokenType.PLUS) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Token operator = tokens.get(current - 1);
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return primary();
    }

    private Expr primary() {
        if (tokens.get(current).type == TokenType.FALSE) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return new Expr.Literal(false);
        }
        if (tokens.get(current).type == TokenType.TRUE) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return new Expr.Literal(true);
        }
        if (tokens.get(current).type == TokenType.NUMBER
            || tokens.get(current).type == TokenType.STRING) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return new Expr.Literal(tokens.get(current - 1).literal);
        }
        if (tokens.get(current).type == TokenType.IDENTIFIER) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return new Expr.Variable(tokens.get(current - 1));
        }
        if (tokens.get(current).type == TokenType.LEFT_PAREN) {
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            Expr expr = expression();
            if (tokens.get(current).type != TokenType.RIGHT_PAREN) {
                String where;
                if (tokens.get(current).type == TokenType.EOF) {
                    where = " at end";
                } else {
                    where = " at '" + tokens.get(current).lexeme + "'";
                }
                diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
                    tokens.get(current).line, ERR_RIGHT_PAREN_AFTER_EXPR + where));
                throw new ParseError();
            }
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
            return new Expr.Grouping(expr);
        }

        String where;
        if (tokens.get(current).type == TokenType.EOF) {
            where = " at end";
        } else {
            where = " at '" + tokens.get(current).lexeme + "'";
        }
        diagnostics.add(new Diagnostic(Diagnostic.Stage.PARSER,
            tokens.get(current).line, ERR_EXPECT_EXPRESSION + where));
        throw new ParseError();
    }

    private void synchronize() {
        if (tokens.get(current).type != TokenType.EOF) {
            current++;
        }
        while (tokens.get(current).type != TokenType.EOF) {
            if (tokens.get(current - 1).type == TokenType.SEMICOLON) {
                return;
            }
            TokenType t = tokens.get(current).type;
            if (t == TokenType.VAR || t == TokenType.FOR
                || t == TokenType.IF || t == TokenType.PRINT) {
                return;
            }
            if (tokens.get(current).type != TokenType.EOF) {
                current++;
            }
        }
    }
}
