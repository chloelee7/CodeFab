package codefab.shell;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Command-line entry point — a mode dispatcher (GoF Strategy). The first
 * argument selects a {@link Mode}:
 * <ul>
 *   <li>no args         -> {@link ReplMode} (interactive {@link PromptShell})</li>
 *   <li>{@code run <f>}  -> {@link RunMode} (file mode, run once)</li>
 *   <li>{@code debug <f>}-> {@link DebugMode} (interactive {@link Debugger})</li>
 *   <li>{@code --help}/{@code -h} -> usage</li>
 * </ul>
 *
 * <p>Backward compatible: if the first argument is not a known subcommand it is
 * treated as a file path, i.e. {@code codefab <file>} == {@code codefab run <file>}.
 *
 * <p>{@link #dispatch} selects the mode and returns its exit code without calling
 * {@code System.exit}, so it is unit-testable; {@code main} maps that code onto
 * the process exit.
 */
public final class Main {

    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int code = dispatch(args, reader, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    /**
     * Pick a mode for {@code args} and run it, returning the exit code. Pure with
     * respect to the process (no {@code System.exit}), so tests can drive any mode
     * with scripted input and assert on output and exit code.
     */
    public static int dispatch(String[] args, BufferedReader in, PrintStream out, PrintStream err) {
        Mode mode = select(args, out);
        if (mode == null) {
            return 0; // help was printed
        }
        return mode.execute(args, in, out, err);
    }

    private static Mode select(String[] args, PrintStream out) {
        if (args.length == 0) {
            return new ReplMode();
        }

        String first = args[0];
        if (first.equals("--help") || first.equals("-h")) {
            printUsage(out);
            return null;
        }
        if (first.equals("run")) {
            return new RunMode(requireFileArg(args, out));
        }
        if (first.equals("debug")) {
            return new DebugMode(requireFileArg(args, out));
        }
        // Backward compatibility: bare file path == `run <file>`.
        return new RunMode(first);
    }

    private static String requireFileArg(String[] args, PrintStream out) {
        if (args.length < 2) {
            // No path given after the subcommand; let RunMode/DebugMode report
            // the unreadable (null-ish) path uniformly. Use empty string so the
            // "Could not read file ''" message is produced.
            return "";
        }
        return args[1];
    }

    private static void printUsage(PrintStream out) {
        out.println("CodeFab Interpreter");
        out.println();
        out.println("Usage:");
        out.println("  codefab                  Start the interactive REPL");
        out.println("  codefab run <file>       Run a CodeFab script file once");
        out.println("  codefab debug <file>     Run a script under the interactive debugger");
        out.println("  codefab <file>           Same as 'run <file>' (backward compatible)");
        out.println("  codefab --help           Show this help");
    }
}
