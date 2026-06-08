package codefab.checker;

import codefab.core.Expr;
import codefab.core.Expr.ArrayGet;
import codefab.core.Expr.ArraySet;
import codefab.core.Expr.Assign;
import codefab.core.Expr.Binary;
import codefab.core.Expr.Call;
import codefab.core.Expr.Grouping;
import codefab.core.Expr.Literal;
import codefab.core.Expr.Logical;
import codefab.core.Expr.Unary;
import codefab.core.Expr.Variable;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;

import java.util.ArrayList;
import java.util.List;

public class ConstantFolder implements Expr.Visitor<Expr> {

    public List<Stmt> fold(List<Stmt> statements) {
        return foldStmts(statements);
    }

    private List<Stmt> foldStmts(List<Stmt> statements) {
        List<Stmt> result = new ArrayList<>();
        for (Stmt stmt : statements) {
            result.add(foldStmt(stmt));
        }
        return result;
    }

    /** null-safe 식 폴딩. */
    private Expr foldExpr(Expr expr) {
        return expr != null ? expr.accept(this) : null;
    }

    /** null-safe 문 폴딩. */
    private Stmt foldStmtOrNull(Stmt stmt) {
        return stmt != null ? foldStmt(stmt) : null;
    }

    private Stmt foldStmt(Stmt stmt) {
        if (stmt instanceof Stmt.ExpressionStmt s) {
            return new Stmt.ExpressionStmt(s.expression().accept(this));
        }
        if (stmt instanceof Stmt.PrintStmt s) {
            return new Stmt.PrintStmt(s.expression().accept(this));
        }
        if (stmt instanceof Stmt.VarStmt s) {
            return new Stmt.VarStmt(s.name(), foldExpr(s.initializer()));
        }
        if (stmt instanceof Stmt.BlockStmt s) {
            return new Stmt.BlockStmt(foldStmts(s.statements()));
        }
        if (stmt instanceof Stmt.IfStmt s) {
            return new Stmt.IfStmt(s.condition().accept(this),
                foldStmt(s.thenBranch()),
                foldStmtOrNull(s.elseBranch()));
        }
        if (stmt instanceof Stmt.WhileStmt s) {
            return new Stmt.WhileStmt(s.condition().accept(this), foldStmt(s.body()));
        }
        if (stmt instanceof Stmt.ForStmt s) {
            return new Stmt.ForStmt(
                foldStmtOrNull(s.initializer()),
                foldExpr(s.condition()),
                foldExpr(s.increment()),
                foldStmt(s.body()));
        }
        if (stmt instanceof Stmt.FunctionStmt s) {
            return new Stmt.FunctionStmt(s.name(), s.params(), foldStmts(s.body()));
        }
        if (stmt instanceof Stmt.ReturnStmt s) {
            return new Stmt.ReturnStmt(s.keyword(), foldExpr(s.value()));
        }
        return stmt;
    }

    // ── Expr visitors ──────────────────────────────────────────────────────────

    @Override
    public Expr visitLiteral(Literal expr) {
        return expr;
    }

    @Override
    public Expr visitVariable(Variable expr) {
        return expr;
    }

    @Override
    public Expr visitAssign(Assign expr) {
        Assign result = new Assign(expr.name, expr.value.accept(this));
        result.distance = expr.distance;
        return result;
    }

    @Override
    public Expr visitUnary(Unary expr) {
        Expr right = expr.right().accept(this);
        if (right instanceof Literal lit) {
            if (expr.operator().type == TokenType.MINUS && lit.value() instanceof Double) {
                return new Literal(-(Double) lit.value());
            }
            if (expr.operator().type == TokenType.PLUS && lit.value() instanceof Double) {
                return lit;
            }
        }
        return new Unary(expr.operator(), right);
    }

    @Override
    public Expr visitBinary(Binary expr) {
        Expr left = expr.left().accept(this);
        Expr right = expr.right().accept(this);

        if (left instanceof Literal l && right instanceof Literal r) {
            if (l.value() instanceof Double lv && r.value() instanceof Double rv) {
                Object result = compute(expr.operator(), lv, rv);
                if (result != null) {
                    return new Literal(result);
                }
            }
        }
        return new Binary(left, expr.operator(), right);
    }

    private Object compute(Token op, double lv, double rv) {
        return switch (op.type) {
            case PLUS -> lv + rv;
            case MINUS -> lv - rv;
            case STAR -> lv * rv;
            // 0 나눗셈은 폴딩만 생략 — 런타임이 오류를 처리하게 둠
            case SLASH -> rv == 0.0 ? null : lv / rv;
            case PERCENT -> rv == 0.0 ? null : lv % rv;
            case GREATER -> lv > rv;
            case GREATER_EQUAL -> lv >= rv;
            case LESS -> lv < rv;
            case LESS_EQUAL -> lv <= rv;
            case EQUAL_EQUAL -> lv == rv;
            case BANG_EQUAL -> lv != rv;
            default -> null;
        };
    }

    @Override
    public Expr visitLogical(Logical expr) {
        return new Logical(expr.left().accept(this), expr.operator(), expr.right().accept(this));
    }

    @Override
    public Expr visitGrouping(Grouping expr) {
        Expr inner = expr.expression().accept(this);
        if (inner instanceof Literal) {
            return inner;
        }
        return new Grouping(inner);
    }

    @Override
    public Expr visitCall(Call expr) {
        return new Call(expr.callee().accept(this), expr.paren(), foldExprs(expr.arguments()));
    }

    private List<Expr> foldExprs(List<Expr> exprs) {
        List<Expr> result = new ArrayList<>();
        for (Expr expr : exprs) {
            result.add(expr.accept(this));
        }
        return result;
    }

    @Override
    public Expr visitArrayGet(ArrayGet expr) {
        return new ArrayGet(expr.array().accept(this), expr.index().accept(this), expr.bracket());
    }

    @Override
    public Expr visitArraySet(ArraySet expr) {
        return new ArraySet(expr.array().accept(this), expr.index().accept(this),
            expr.value().accept(this), expr.bracket());
    }
}
