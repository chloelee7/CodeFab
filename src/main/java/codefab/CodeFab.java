package codefab;

/**
 * Facade for one-shot script execution. Each {@link #run} call uses an
 * independent {@link CodeFabSession}, so scripts do not share state. For an
 * interactive REPL that preserves variables between inputs, use
 * {@link CodeFabSession} directly.
 */
public final class CodeFab {

    /** Run a complete script with fresh interpreter state. */
    public RunResult run(String source) {
        return new CodeFabSession().run(source);
    }
}
