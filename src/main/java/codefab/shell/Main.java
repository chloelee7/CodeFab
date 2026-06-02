package codefab.shell;

import codefab.CodeFab;
import codefab.RunResult;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line entry point.
 * <ul>
 *   <li>no args    -> start the interactive {@link PromptShell}</li>
 *   <li>a file arg -> run that script and exit (non-zero on failure)</li>
 *   <li>--help     -> print usage</li>
 * </ul>
 */
public final class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            new PromptShell(reader, System.out).run();
            return;
        }

        if (args[0].equals("--help") || args[0].equals("-h")) {
            printUsage();
            return;
        }

        runFile(args[0]);
    }

    private static void runFile(String path) {
        String source;
        try {
            source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not read file '" + path + "': " + e.getMessage());
            System.exit(66); // EX_NOINPUT
            return;
        }

        RunResult result = new CodeFab().run(source);
        for (String line : result.output()) {
            System.out.println(line);
        }
        for (Diagnostic diagnostic : result.diagnostics()) {
            System.err.println(diagnostic.render());
        }
        if (!result.success()) {
            System.exit(65); // EX_DATAERR
        }
    }

    private static void printUsage() {
        System.out.println("CodeFab Interpreter");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  codefab            Start the interactive REPL");
        System.out.println("  codefab <file>     Run a CodeFab script file");
        System.out.println("  codefab --help     Show this help");
    }
}
