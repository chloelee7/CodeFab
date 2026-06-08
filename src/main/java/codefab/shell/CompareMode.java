package codefab.shell;

import codefab.CodeFab;
import codefab.RunResult;
import codefab.SelfHostCodeFab;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

public final class CompareMode implements Mode {

    private static final int EX_DATA_ERR = 65;
    private static final int EX_NO_INPUT = 66;

    private final String path;
    private final RuntimeRunner javaRunner;
    private final RuntimeRunner selfhostRunner;

    public CompareMode(String path) {
        this(path, source -> new CodeFab().run(source), source -> new SelfHostCodeFab().run(source));
    }

    CompareMode(String path, RuntimeRunner javaRunner, RuntimeRunner selfhostRunner) {
        this.path = path;
        this.javaRunner = javaRunner;
        this.selfhostRunner = selfhostRunner;
    }

    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        String source;
        try {
            source = ShellFiles.readUtf8(path);
        } catch (IOException e) {
            ShellFiles.printReadError(err, path);
            return EX_NO_INPUT;
        }

        TimedResult javaResult = runTimed(javaRunner, source);
        TimedResult selfhostResult = runTimed(selfhostRunner, source);
        List<String> javaDiagnostics = renderedDiagnostics(javaResult.result);
        List<String> selfhostDiagnostics = renderedDiagnostics(selfhostResult.result);
        boolean successMatches = javaResult.result.success() == selfhostResult.result.success();
        boolean outputMatches = javaResult.result.output().equals(selfhostResult.result.output());
        boolean diagnosticsMatches = javaDiagnostics.equals(selfhostDiagnostics);
        boolean matches = successMatches && outputMatches && diagnosticsMatches;

        out.printf("%-9s %-4s %dms%n", "Java", status(javaResult.result), javaResult.elapsedMillis);
        out.printf("%-9s %-4s %dms%n", "Selfhost", status(selfhostResult.result), selfhostResult.elapsedMillis);
        out.println();
        out.println("Success: " + comparisonText(successMatches));
        out.println("Output: " + comparisonText(outputMatches));
        out.println("Diagnostics: " + comparisonText(diagnosticsMatches));

        printSection(out, "Java output", javaResult.result.output(), !outputMatches);
        printSection(out, "Selfhost output", selfhostResult.result.output(), !outputMatches);
        printSection(out, "Diagnostics (both)", javaDiagnostics, diagnosticsMatches && !javaDiagnostics.isEmpty());
        printSection(out, "Java diagnostics", javaDiagnostics, !diagnosticsMatches);
        printSection(out, "Selfhost diagnostics", selfhostDiagnostics, !diagnosticsMatches);

        return matches ? 0 : EX_DATA_ERR;
    }

    private TimedResult runTimed(RuntimeRunner runner, String source) {
        long start = System.nanoTime();
        RunResult result = runner.run(source);
        return new TimedResult(result, elapsedMillis(start));
    }

    private static long elapsedMillis(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static String status(RunResult result) {
        return result.success() ? "PASS" : "FAIL";
    }

    private static String comparisonText(boolean matches) {
        return matches ? "identical" : "different";
    }

    private static List<String> renderedDiagnostics(RunResult result) {
        return result.diagnostics().stream()
            .map(Diagnostic::render)
            .toList();
    }

    private static void printSection(PrintStream out, String title, List<String> lines, boolean shouldPrint) {
        if (!shouldPrint) {
            return;
        }
        out.println();
        out.println(title + ":");
        if (lines.isEmpty()) {
            out.println("(none)");
            return;
        }
        for (String line : lines) {
            out.println(line);
        }
    }

    private record TimedResult(RunResult result, long elapsedMillis) {
    }

    @FunctionalInterface
    interface RuntimeRunner {
        RunResult run(String source);
    }
}
