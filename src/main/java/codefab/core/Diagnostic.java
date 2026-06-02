package codefab.core;

/**
 * A single problem report produced by any stage of the pipeline. The stage is
 * recorded so callers can tell scanner/parser/checker/runtime errors apart.
 */
public final class Diagnostic {
    public enum Stage { SCANNER, PARSER, CHECKER, RUNTIME }

    public final Stage stage;
    public final int line;
    public final String message;

    public Diagnostic(Stage stage, int line, String message) {
        this.stage = stage;
        this.line = line;
        this.message = message;
    }

    /** Human-readable rendering including the line number when available. */
    public String render() {
        if (line > 0) {
            return "[line " + line + "] " + stage + " error: " + message;
        }
        return stage + " error: " + message;
    }

    @Override
    public String toString() {
        return render();
    }
}
