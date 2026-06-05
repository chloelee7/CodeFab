package codefab.checker;

import codefab.core.Diagnostic;
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

/**
 * 상수 폴딩 최적화 패스. 양쪽 피연산자가 모두 Literal(Number)인 Binary 표현식을
 * 컴파일 타임에 계산해 Literal로 치환한다. Checker 이후, Executor 이전에 실행된다.
 */
public class ConstantFolder implements Expr.Visitor<Expr> {

    private List<Diagnostic> diagnostics;

    public List<Stmt> fold(List<Stmt> statements, List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
        List<Stmt> result = new ArrayList<>();
        for (Stmt stmt : statements) {
            result.add(foldStmt(stmt));
        }
        return result;
    }

    private Stmt foldStmt(Stmt stmt) {
        if (stmt instanceof Stmt.ExpressionStmt) {
            Stmt.ExpressionStmt s = (Stmt.ExpressionStmt) stmt;
            return new Stmt.ExpressionStmt(s.expression.accept(this));
        }
        if (stmt instanceof Stmt.PrintStmt) {
            Stmt.PrintStmt s = (Stmt.PrintStmt) stmt;
            return new Stmt.PrintStmt(s.expression.accept(this));
        }
        if (stmt instanceof Stmt.VarStmt) {
            Stmt.VarStmt s = (Stmt.VarStmt) stmt;
            Expr init = s.initializer != null ? s.initializer.accept(this) : null;
            return new Stmt.VarStmt(s.name, init);
        }
        if (stmt instanceof Stmt.BlockStmt) {
            Stmt.BlockStmt s = (Stmt.BlockStmt) stmt;
            List<Stmt> folded = new ArrayList<>();
            for (Stmt inner : s.statements) folded.add(foldStmt(inner));
            return new Stmt.BlockStmt(folded);
        }
        if (stmt instanceof Stmt.IfStmt) {
            Stmt.IfStmt s = (Stmt.IfStmt) stmt;
            return new Stmt.IfStmt(s.condition.accept(this),
                    foldStmt(s.thenBranch),
                    s.elseBranch != null ? foldStmt(s.elseBranch) : null);
        }
        if (stmt instanceof Stmt.WhileStmt) {
            Stmt.WhileStmt s = (Stmt.WhileStmt) stmt;
            return new Stmt.WhileStmt(s.condition.accept(this), foldStmt(s.body));
        }
        if (stmt instanceof Stmt.ForStmt) {
            Stmt.ForStmt s = (Stmt.ForStmt) stmt;
            return new Stmt.ForStmt(
                    s.initializer != null ? foldStmt(s.initializer) : null,
                    s.condition != null ? s.condition.accept(this) : null,
                    s.increment != null ? s.increment.accept(this) : null,
                    foldStmt(s.body));
        }
        if (stmt instanceof Stmt.FunctionStmt) {
            Stmt.FunctionStmt s = (Stmt.FunctionStmt) stmt;
            List<Stmt> folded = new ArrayList<>();
            for (Stmt inner : s.body) folded.add(foldStmt(inner));
            return new Stmt.FunctionStmt(s.name, s.params, folded);
        }
        if (stmt instanceof Stmt.ReturnStmt) {
            Stmt.ReturnStmt s = (Stmt.ReturnStmt) stmt;
            Expr val = s.value != null ? s.value.accept(this) : null;
            return new Stmt.ReturnStmt(s.keyword, val);
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
        Expr right = expr.right.accept(this);
        if (right instanceof Literal) {
            Literal lit = (Literal) right;
            if (expr.operator.type == TokenType.MINUS && lit.value instanceof Double) {
                return new Literal(-(Double) lit.value);
            }
            if (expr.operator.type == TokenType.PLUS && lit.value instanceof Double) {
                return lit;
            }
        }
        return new Unary(expr.operator, right);
    }

    @Override
    public Expr visitBinary(Binary expr) {
        Expr left = expr.left.accept(this);
        Expr right = expr.right.accept(this);

        if (left instanceof Literal && right instanceof Literal) {
            Literal l = (Literal) left;
            Literal r = (Literal) right;
            if (l.value instanceof Double && r.value instanceof Double) {
                Double lv = (Double) l.value;
                Double rv = (Double) r.value;
                Object result = compute(expr.operator, lv, rv);
                if (result != null) {
                    return new Literal(result);
                }
            }
        }
        return new Binary(left, expr.operator, right);
    }

    private Object compute(Token op, double lv, double rv) {
        switch (op.type) {
            case PLUS:  return lv + rv;
            case MINUS: return lv - rv;
            case STAR:  return lv * rv;
            case SLASH:
                if (rv == 0.0) {
                    diagnostics.add(new Diagnostic(
                            Diagnostic.Stage.CHECKER, op.line, "Division by zero."));
                    return null;
                }
                return lv / rv;
            case GREATER:       return lv > rv;
            case GREATER_EQUAL: return lv >= rv;
            case LESS:          return lv < rv;
            case LESS_EQUAL:    return lv <= rv;
            case EQUAL_EQUAL:   return lv == rv;
            case BANG_EQUAL:    return lv != rv;
            default:            return null;
        }
    }

    @Override
    public Expr visitLogical(Logical expr) {
        return new Logical(expr.left.accept(this), expr.operator, expr.right.accept(this));
    }

    @Override
    public Expr visitGrouping(Grouping expr) {
        Expr inner = expr.expression.accept(this);
        if (inner instanceof Literal) return inner;
        return new Grouping(inner);
    }

    @Override
    public Expr visitCall(Call expr) {
        List<Expr> args = new ArrayList<>();
        for (Expr arg : expr.arguments) args.add(arg.accept(this));
        return new Call(expr.callee.accept(this), expr.paren, args);
    }

    @Override
    public Expr visitArrayGet(ArrayGet expr) {
        return new ArrayGet(expr.array.accept(this), expr.index.accept(this), expr.bracket);
    }

    @Override
    public Expr visitArraySet(ArraySet expr) {
        return new ArraySet(expr.array.accept(this), expr.index.accept(this),
                expr.value.accept(this), expr.bracket);
    }
}
