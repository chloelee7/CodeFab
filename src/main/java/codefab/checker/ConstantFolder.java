package codefab.checker;

import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * AST-to-AST optimization pass that collapses sub-expressions whose value is
 * 100% determined before execution into a single {@link Expr.Literal} (contract
 * §9-2). Nodes are immutable, so folding never mutates an existing node: it
 * returns a brand-new tree, reusing original nodes wherever nothing folded.
 *
 * <p>Foldable: {@code Literal}; {@code Binary}/{@code Unary}/{@code Grouping}/
 * {@code Logical} whose operands all fold to constants (number arithmetic and
 * comparisons, boolean logic, unary {@code -}/{@code !}, and string {@code +}).
 * Anything containing a {@code Variable}/{@code Assign}/{@code Call}/{@code Index}/
 * {@code IndexSet} is not foldable, though enclosing nodes may still fold the
 * parts that are.
 *
 * <p>Safety: if folding would divide or mod by zero, that sub-expression is left
 * unfolded so the Executor reports {@code Division by zero.} at runtime. Folding
 * must never change runtime meaning.
 */
final class ConstantFolder {

    List<Stmt> fold(List<Stmt> statements) {
        List<Stmt> result = new ArrayList<>(statements.size());
        for (Stmt stmt : statements) {
            result.add(fold(stmt));
        }
        return result;
    }

    // --- statements --------------------------------------------------------

    private Stmt fold(Stmt stmt) {
        if (stmt == null) return null;
        if (stmt instanceof Stmt.ExpressionStmt) {
            Stmt.ExpressionStmt s = (Stmt.ExpressionStmt) stmt;
            return new Stmt.ExpressionStmt(s.line, fold(s.expression));
        }
        if (stmt instanceof Stmt.PrintStmt) {
            Stmt.PrintStmt s = (Stmt.PrintStmt) stmt;
            return new Stmt.PrintStmt(s.line, fold(s.expression));
        }
        if (stmt instanceof Stmt.VarStmt) {
            Stmt.VarStmt s = (Stmt.VarStmt) stmt;
            Expr init = s.initializer == null ? null : fold(s.initializer);
            return new Stmt.VarStmt(s.line, s.name, init);
        }
        if (stmt instanceof Stmt.BlockStmt) {
            Stmt.BlockStmt s = (Stmt.BlockStmt) stmt;
            return new Stmt.BlockStmt(s.line, fold(s.statements));
        }
        if (stmt instanceof Stmt.IfStmt) {
            Stmt.IfStmt s = (Stmt.IfStmt) stmt;
            return new Stmt.IfStmt(s.line, fold(s.condition), fold(s.thenBranch),
                    s.elseBranch == null ? null : fold(s.elseBranch));
        }
        if (stmt instanceof Stmt.ForStmt) {
            Stmt.ForStmt s = (Stmt.ForStmt) stmt;
            return new Stmt.ForStmt(s.line,
                    s.initializer == null ? null : fold(s.initializer),
                    s.condition == null ? null : fold(s.condition),
                    s.increment == null ? null : fold(s.increment),
                    fold(s.body));
        }
        if (stmt instanceof Stmt.WhileStmt) {
            Stmt.WhileStmt s = (Stmt.WhileStmt) stmt;
            return new Stmt.WhileStmt(s.line, fold(s.condition), fold(s.body));
        }
        if (stmt instanceof Stmt.FunctionStmt) {
            Stmt.FunctionStmt s = (Stmt.FunctionStmt) stmt;
            return new Stmt.FunctionStmt(s.line, s.name, s.params, fold(s.body));
        }
        if (stmt instanceof Stmt.ReturnStmt) {
            Stmt.ReturnStmt s = (Stmt.ReturnStmt) stmt;
            return new Stmt.ReturnStmt(s.line, s.keyword,
                    s.value == null ? null : fold(s.value));
        }
        return stmt;
    }

    // --- expressions -------------------------------------------------------

    private Expr fold(Expr expr) {
        if (expr instanceof Expr.Literal) {
            return expr;
        }
        if (expr instanceof Expr.Grouping) {
            Expr.Grouping g = (Expr.Grouping) expr;
            Expr inner = fold(g.expression);
            // A grouping around a constant collapses to that constant.
            if (inner instanceof Expr.Literal) return inner;
            return new Expr.Grouping(inner);
        }
        if (expr instanceof Expr.Unary) {
            return foldUnary((Expr.Unary) expr);
        }
        if (expr instanceof Expr.Binary) {
            return foldBinary((Expr.Binary) expr);
        }
        if (expr instanceof Expr.Logical) {
            return foldLogical((Expr.Logical) expr);
        }
        if (expr instanceof Expr.Assign) {
            Expr.Assign a = (Expr.Assign) expr;
            // Assign itself blocks folding, but its value sub-tree may fold.
            return new Expr.Assign(a.name, fold(a.value));
        }
        if (expr instanceof Expr.Call) {
            Expr.Call c = (Expr.Call) expr;
            List<Expr> args = new ArrayList<>(c.arguments.size());
            for (Expr arg : c.arguments) args.add(fold(arg));
            return new Expr.Call(fold(c.callee), c.paren, args);
        }
        if (expr instanceof Expr.Index) {
            Expr.Index i = (Expr.Index) expr;
            return new Expr.Index(fold(i.target), i.bracket, fold(i.index));
        }
        if (expr instanceof Expr.IndexSet) {
            Expr.IndexSet i = (Expr.IndexSet) expr;
            return new Expr.IndexSet(fold(i.target), i.bracket, fold(i.index), fold(i.value));
        }
        // Variable and anything unknown: not foldable, returned as-is.
        return expr;
    }

    private Expr foldUnary(Expr.Unary expr) {
        Expr right = fold(expr.right);
        if (right instanceof Expr.Literal) {
            Object v = ((Expr.Literal) right).value;
            switch (expr.operator.type) {
                case MINUS:
                    if (v instanceof Double) return new Expr.Literal(-(double) v);
                    break;
                case BANG:
                    if (v instanceof Boolean) return new Expr.Literal(!(boolean) v);
                    break;
                default:
                    break;
            }
        }
        if (right == expr.right) return expr;
        return new Expr.Unary(expr.operator, right);
    }

    private Expr foldBinary(Expr.Binary expr) {
        Expr left = fold(expr.left);
        Expr right = fold(expr.right);
        if (left instanceof Expr.Literal && right instanceof Expr.Literal) {
            Object l = ((Expr.Literal) left).value;
            Object r = ((Expr.Literal) right).value;
            Object folded = applyBinary(expr.operator.type, l, r);
            if (folded != null) return new Expr.Literal(folded);
        }
        if (left == expr.left && right == expr.right) return expr;
        return new Expr.Binary(left, expr.operator, right);
    }

    /**
     * Returns the constant result of a binary op on two constants, or {@code null}
     * if it is not foldable (mismatched types, divide-by-zero, etc.) so the
     * caller leaves the original tree intact for the runtime to handle.
     */
    private Object applyBinary(TokenType op, Object l, Object r) {
        if (l instanceof Double && r instanceof Double) {
            double a = (double) l;
            double b = (double) r;
            switch (op) {
                case PLUS: return a + b;
                case MINUS: return a - b;
                case STAR: return a * b;
                case SLASH:
                    if (b == 0.0) return null; // preserve runtime Division by zero.
                    return a / b;
                case PERCENT:
                    if (b == 0.0) return null; // preserve runtime Division by zero.
                    return a % b;
                case GREATER: return a > b;
                case GREATER_EQUAL: return a >= b;
                case LESS: return a < b;
                case LESS_EQUAL: return a <= b;
                case EQUAL_EQUAL: return a == b;
                case BANG_EQUAL: return a != b;
                default: return null;
            }
        }
        if (l instanceof String && r instanceof String && op == TokenType.PLUS) {
            return (String) l + (String) r;
        }
        return null;
    }

    private Expr foldLogical(Expr.Logical expr) {
        Expr left = fold(expr.left);
        Expr right = fold(expr.right);
        if (left instanceof Expr.Literal && right instanceof Expr.Literal) {
            Object l = ((Expr.Literal) left).value;
            Object r = ((Expr.Literal) right).value;
            if (l instanceof Boolean && r instanceof Boolean) {
                boolean a = (boolean) l;
                boolean b = (boolean) r;
                if (expr.operator.type == TokenType.OR) {
                    return new Expr.Literal(a || b);
                }
                return new Expr.Literal(a && b);
            }
        }
        if (left == expr.left && right == expr.right) return expr;
        return new Expr.Logical(left, expr.operator, right);
    }
}
