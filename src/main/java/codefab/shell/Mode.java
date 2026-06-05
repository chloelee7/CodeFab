package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

/**
 * One CLI execution strategy (GoF Strategy). {@link Main} picks a mode from the
 * first command-line argument and runs it. Every mode is given its input reader
 * and output/error streams so it can be driven by a test with scripted input
 * instead of the real console.
 *
 * @see ReplMode
 * @see RunMode
 * @see DebugMode
 */
public interface Mode {
    /**
     * Execute this mode.
     *
     * @param args the full argument vector (e.g. {@code ["run", "prog.txt"]});
     *             a mode reads whatever positions it needs.
     * @param in   the input source (REPL/debug command lines)
     * @param out  the standard output stream
     * @param err  the diagnostic stream
     * @return a process exit code (0 = success).
     */
    int execute(String[] args, BufferedReader in, PrintStream out, PrintStream err);
}
