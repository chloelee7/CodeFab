package codefab.shell;

import codefab.CodeFab;
import codefab.RunResult;
import codefab.SelfHostCodeFab;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CompareMode implements Mode {

    private static final int EX_DATA_ERR = 65;
    private static final int EX_NO_INPUT = 66;

    private final String path;

    public CompareMode(String path) {
        this.path = path;
    }

    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        String source;
        try {
            source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("Error: file not found: " + path);
            return EX_NO_INPUT;
        }

        TimedResult javaResult = runJava(source);
        TimedResult selfhostResult = runSelfhost(source);
        boolean successMatches = javaResult.result.success() == selfhostResult.result.success();
        boolean outputMatches = javaResult.result.output().equals(selfhostResult.result.output());
        boolean diagnosticsMatches = renderedDiagnostics(javaResult.result)
            .equals(renderedDiagnostics(selfhostResult.result));
        boolean matches = successMatches && outputMatches && diagnosticsMatches;

        out.printf("%-9s %-4s %dms%n", "Java", status(javaResult.result), javaResult.elapsedMillis);
        out.printf("%-9s %-4s %dms%n", "Selfhost", status(selfhostResult.result), selfhostResult.elapsedMillis);
        out.println();
        out.println("Success: " + comparisonText(successMatches));
        out.println("Output: " + comparisonText(outputMatches));
        out.println("Diagnostics: " + comparisonText(diagnosticsMatches));

        printSection(out, "Java output", javaResult.result.output(), !outputMatches);
        printSection(out, "Selfhost output", selfhostResult.result.output(), !outputMatches);
        printSection(out, "Diagnostics", renderedDiagnostics(javaResult.result),
            diagnosticsMatches && !javaResult.result.diagnostics().isEmpty());
        printSection(out, "Java diagnostics", renderedDiagnostics(javaResult.result), !diagnosticsMatches);
        printSection(out, "Selfhost diagnostics", renderedDiagnostics(selfhostResult.result), !diagnosticsMatches);

        return matches ? 0 : EX_DATA_ERR;
    }

    private TimedResult runJava(String source) {
        long start = System.nanoTime();
        RunResult result = new CodeFab().run(source);
        return new TimedResult(result, elapsedMillis(start));
    }

    private TimedResult runSelfhost(String source) {
        long start = System.nanoTime();
        RunResult result = new SelfHostCodeFab().run(source);
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
}
