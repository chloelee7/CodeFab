package codefab.executor;

import codefab.core.Stmt;

/**
 * A debug-time hook the Executor notifies immediately before it runs each
 * statement (contract §10-1). The Shell's {@code Debugger} plugs into this to
 * stop, step and watch at statement granularity.
 *
 * <p>When no observer is set the Executor pays no cost: the notify call is
 * guarded by a single null check and depth bookkeeping is skipped, so ordinary
 * execution is unaffected.
 */
public interface ExecutionObserver {
    /**
     * Called just before {@code stmt} is executed.
     *
     * @param stmt  the statement about to run
     * @param line  its source line ({@code stmt.line})
     * @param env   the current Environment (use {@link Environment#bindings()}
     *              / {@link Environment#enclosing()} to inspect/watch)
     * @param depth the current block-nesting depth (top level = 0); a block or
     *              function call frame adds 1. Used by {@code next} (step over)
     *              to ignore notifications until execution returns to the same
     *              or a shallower depth.
     */
    void beforeStmt(Stmt stmt, int line, Environment env, int depth);
}
