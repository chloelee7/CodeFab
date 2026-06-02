package codefab;

import codefab.core.Diagnostic;

import java.util.Collections;
import java.util.List;

/**
 * Outcome of running source through the pipeline: the lines printed, any
 * diagnostics produced, and whether the run completed without error.
 */
public final class RunResult {
    private final boolean success;
    private final List<String> output;
    private final List<Diagnostic> diagnostics;

    public RunResult(boolean success, List<String> output, List<Diagnostic> diagnostics) {
        this.success = success;
        this.output = List.copyOf(output);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public boolean success() {
        return success;
    }

    public List<String> output() {
        return Collections.unmodifiableList(output);
    }

    public List<Diagnostic> diagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    /** Convenience: all diagnostic messages joined by newlines. */
    public String diagnosticText() {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(d.render());
        }
        return sb.toString();
    }
}
