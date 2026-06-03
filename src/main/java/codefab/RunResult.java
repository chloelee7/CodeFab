package codefab;

import codefab.core.Diagnostic;

import java.util.List;

public final class RunResult {
    private final boolean success;
    private final List<String> output;
    private final List<Diagnostic> diagnostics;

    public RunResult(boolean success, List<String> output, List<Diagnostic> diagnostics) {
        this.success = success;
        this.output = output;
        this.diagnostics = diagnostics;
    }

    public boolean success() {
        throw new UnsupportedOperationException("success not implemented");
    }

    public List<String> output() {
        throw new UnsupportedOperationException("output not implemented");
    }

    public List<Diagnostic> diagnostics() {
        throw new UnsupportedOperationException("diagnostics not implemented");
    }

    public String diagnosticText() {
        throw new UnsupportedOperationException("diagnosticText not implemented");
    }
}
