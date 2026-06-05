package codefab.checker;

import codefab.core.Expr;
import codefab.core.Stmt;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of a {@link Checker} pass.
 *
 * <p>It carries the two pre-execution optimizations the Checker produces
 * (contract §6, §9): the constant-folded {@link #program()} (a new AST in which
 * every fully-constant sub-expression has been collapsed to a single
 * {@code Expr.Literal}) and the static-binding {@link #locals()} map (each
 * {@code Variable}/{@code Assign} node mapped to the scope distance at which its
 * name is found). The Executor runs {@code program()} and uses {@code locals()}
 * for O(1) variable access.
 *
 * <p>The keys of {@code locals()} are nodes from {@code program()} (the folded
 * tree), so they line up exactly with what the Executor walks. Diagnostics are
 * not held here; they are accumulated into the list injected into the Checker.
 */
public final class CheckResult {
    private final List<Stmt> program;
    private final Map<Expr, Integer> locals;

    public CheckResult(List<Stmt> program, Map<Expr, Integer> locals) {
        this.program = Collections.unmodifiableList(program);
        this.locals = Collections.unmodifiableMap(locals);
    }

    /** The constant-folded program the Executor should run. */
    public List<Stmt> program() {
        return program;
    }

    /** Static-binding distances for resolved {@code Variable}/{@code Assign} nodes. */
    public Map<Expr, Integer> locals() {
        return locals;
    }
}
