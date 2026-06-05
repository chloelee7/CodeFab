package codefab.checker;

import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Expr.Assign;
import codefab.core.Expr.ArrayGet;
import codefab.core.Expr.ArraySet;
import codefab.core.Expr.Binary;
import codefab.core.Expr.Call;
import codefab.core.Expr.Grouping;
import codefab.core.Expr.Literal;
import codefab.core.Expr.Logical;
import codefab.core.Expr.Unary;
import codefab.core.Expr.Variable;
import codefab.core.Stmt;
import codefab.core.Stmt.BlockStmt;
import codefab.core.Stmt.ExpressionStmt;
import codefab.core.Stmt.ForStmt;
import codefab.core.Stmt.FunctionStmt;
import codefab.core.Stmt.IfStmt;
import codefab.core.Stmt.PrintStmt;
import codefab.core.Stmt.ReturnStmt;
import codefab.core.Stmt.VarStmt;
import codefab.core.Stmt.WhileStmt;
import codefab.core.Token;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Checker implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    // true  → REPL 모드: 전역 스코프 미선언/재선언은 Executor에 위임
    // false → 정적 분석 모드: 전역 포함 전체 스코프 검사 (CheckerTest 전용)
    private final boolean replMode;
    private List<Diagnostic> diagnostics;
    private Deque<Set<String>> scopes;
    private String initializingVar;
    private boolean inFunction = false;

    public Checker() {
        this.replMode = false;
        this.diagnostics = null;
    }

    public Checker(List<Diagnostic> diagnostics) {
        this.replMode = true;
        this.diagnostics = diagnostics;
    }

    public List<Diagnostic> check(List<Stmt> statements) {
        if (this.diagnostics == null) {
            this.diagnostics = new ArrayList<>();
        }
        this.initializingVar = null;
        this.scopes = new ArrayDeque<>();
        this.scopes.push(new HashSet<>());
        for (Stmt stmt : statements) {
            stmt.accept(this);
        }
        return this.diagnostics;
    }

    // ── Expr visitors ──────────────────────────────────────────────────────────

    @Override
    public Void visitLiteral(Literal expr) {
        return null;
    }

    @Override
    public Void visitVariable(Variable expr) {
        checkDeclared(expr.name);
        return null;
    }

    @Override
    public Void visitAssign(Assign expr) {
        expr.value.accept(this);
        checkDeclared(expr.name);
        return null;
    }

    @Override
    public Void visitUnary(Unary expr) {
        expr.right.accept(this);
        return null;
    }

    @Override
    public Void visitBinary(Binary expr) {
        visitChildren(expr.left, expr.right);
        return null;
    }

    @Override
    public Void visitLogical(Logical expr) {
        visitChildren(expr.left, expr.right);
        return null;
    }

    @Override
    public Void visitGrouping(Grouping expr) {
        expr.expression.accept(this);
        return null;
    }

    @Override
    public Void visitCall(Call expr) {
        expr.callee.accept(this);
        for (Expr arg : expr.arguments) {
            arg.accept(this);
        }
        return null;
    }

    @Override
    public Void visitArrayGet(ArrayGet expr) {
        expr.array.accept(this);
        expr.index.accept(this);
        return null;
    }

    @Override
    public Void visitArraySet(ArraySet expr) {
        expr.array.accept(this);
        expr.index.accept(this);
        expr.value.accept(this);
        return null;
    }

    // ── Stmt visitors ──────────────────────────────────────────────────────────

    @Override
    public Void visitExpressionStmt(ExpressionStmt stmt) {
        stmt.expression.accept(this);
        return null;
    }

    @Override
    public Void visitPrintStmt(PrintStmt stmt) {
        stmt.expression.accept(this);
        return null;
    }

    @Override
    public Void visitVarStmt(VarStmt stmt) {
        String previous = this.initializingVar;
        this.initializingVar = stmt.name.lexeme;
        try {
            visitIfPresent(stmt.initializer);
        } finally {
            this.initializingVar = previous;
        }
        declare(stmt.name);
        return null;
    }

    @Override
    public Void visitBlockStmt(BlockStmt stmt) {
        withNewScope(() -> {
            for (Stmt s : stmt.statements) {
                s.accept(this);
            }
        });
        return null;
    }

    @Override
    public Void visitIfStmt(IfStmt stmt) {
        stmt.condition.accept(this);
        stmt.thenBranch.accept(this);
        visitIfPresent(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitForStmt(ForStmt stmt) {
        withNewScope(() -> {
            visitIfPresent(stmt.initializer);
            visitIfPresent(stmt.condition);
            stmt.body.accept(this);
            visitIfPresent(stmt.increment);
        });
        return null;
    }

    @Override
    public Void visitWhileStmt(WhileStmt stmt) {
        stmt.condition.accept(this);
        stmt.body.accept(this);
        return null;
    }

    @Override
    public Void visitFunctionStmt(FunctionStmt stmt) {
        declare(stmt.name);

        // 파라미터 이름 중복 검사
        Set<String> paramNames = new HashSet<>();
        for (Token param : stmt.params) {
            if (!paramNames.add(param.lexeme)) {
                error(param.line, "Already a parameter with this name.");
            }
        }

        boolean previousInFunction = this.inFunction;
        this.inFunction = true;
        withNewScope(() -> {
            for (Token param : stmt.params) {
                declare(param);
            }
            for (Stmt s : stmt.body) {
                s.accept(this);
            }
        });
        this.inFunction = previousInFunction;
        return null;
    }

    @Override
    public Void visitReturnStmt(ReturnStmt stmt) {
        if (!this.inFunction) {
            error(stmt.keyword.line, "Can't return from top-level code.");
        }
        visitIfPresent(stmt.value);
        return null;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private void visitChildren(Expr left, Expr right) {
        left.accept(this);
        right.accept(this);
    }

    private void withNewScope(Runnable body) {
        this.scopes.push(new HashSet<>());
        body.run();
        this.scopes.pop();
    }

    private void visitIfPresent(Expr expr) {
        if (expr != null) expr.accept(this);
    }

    private void visitIfPresent(Stmt stmt) {
        if (stmt != null) stmt.accept(this);
    }

    private void declare(Token name) {
        boolean isGlobalScope = false;
        if (this.scopes.isEmpty()) {
            throw new RuntimeException("scopes is empty");
        } else if (this.scopes.size() == 1) {
            isGlobalScope = true;
        }

        Set<String> current = this.scopes.peek();

        if (current.contains(name.lexeme) && !(this.replMode && isGlobalScope)) {
            error(name.line, "Already a variable with this name in this scope.");
        }
        current.add(name.lexeme);
    }

    private void checkDeclared(Token name) {
        if (name.lexeme.equals(this.initializingVar)) {
            error(name.line, "Can't read local variable in initializer.");
            return;
        }
        if (this.replMode && this.scopes.size() <= 1) {
            return;
        }
        for (Set<String> scope : this.scopes) {
            if (scope.contains(name.lexeme)) return;
        }
        error(name.line, "undefined variable '" + name.lexeme + "'");
    }

    private void error(int line, String message) {
        this.diagnostics.add(new Diagnostic(Diagnostic.Stage.CHECKER, line, message));
    }
}
