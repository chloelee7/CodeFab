package codefab.shell;

import codefab.CodeFab;
import codefab.RunResult;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * File mode: read a {@code .txt} script and run it once.
 *
 * <p>Output goes to {@code out}, diagnostics to {@code err}. Diagnostics render
 * with their line number (see {@link Diagnostic#render()}), so a runtime fault is
 * reported as {@code [line N] RUNTIME error: ...}. The process exit code is
 * separated from the I/O so tests can assert it:
 * <ul>
 *   <li>{@code 0}  — ran with no diagnostics</li>
 *   <li>{@code 65} — pipeline produced diagnostics (syntax/static/runtime)</li>
 *   <li>{@code 66} — the file could not be read</li>
 * </ul>
 */
public final class RunMode implements Mode {
    public static final int EXIT_OK = 0;
    public static final int EXIT_DATA_ERROR = 65; // EX_DATAERR
    public static final int EXIT_NO_INPUT = 66;   // EX_NOINPUT

    private final String path;

    /** @param path the script path to run (already resolved from the argv). */
    public RunMode(String path) {
        this.path = path;
    }

    @Override
    public int execute(String[] args, BufferedReader in, PrintStream out, PrintStream err) {
        String source;
        try {
            source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            err.println("Could not read file '" + path + "'");
            return EXIT_NO_INPUT;
        } catch (IOException e) {
            err.println("Could not read file '" + path + "'");
            return EXIT_NO_INPUT;
        }

        RunResult result = new CodeFab().run(source);
        for (String line : result.output()) {
            out.println(line);
        }
        for (Diagnostic diagnostic : result.diagnostics()) {
            err.println(diagnostic.render());
        }
        return result.success() ? EXIT_OK : EXIT_DATA_ERROR;
    }
}
