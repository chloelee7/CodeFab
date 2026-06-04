package codefab;

import codefab.core.Diagnostic;

import java.util.Collections;
import java.util.List;

public record RunResult(boolean success, List<String> output, List<Diagnostic> diagnostics) {

    public RunResult(boolean success, List<String> output, List<Diagnostic> diagnostics) {
        this.success = success;
        this.output = List.copyOf(output);
        this.diagnostics = List.copyOf(diagnostics);
    }

    @Override
    public List<String> output() {
        return Collections.unmodifiableList(output);
    }

    @Override
    public List<Diagnostic> diagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public String diagnosticText() {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(d.render());
        }
        return sb.toString();
    }
}
