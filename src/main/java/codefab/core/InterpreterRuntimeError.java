package codefab.core;

/**
 * Thrown by the Executor when a program hits a runtime fault. Carries the
 * token nearest the fault so the line number can be reported.
 */
public final class InterpreterRuntimeError extends RuntimeException {
    public final Token token;

    public InterpreterRuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }

    public int line() {
        return token != null ? token.line : 0;
    }
}
