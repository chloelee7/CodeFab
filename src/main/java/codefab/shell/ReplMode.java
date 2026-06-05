package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

/**
 * Interactive prompt mode (no arguments): delegates to {@link PromptShell}, the
 * persistent multi-line REPL. Variables survive between inputs; {@code :exit} /
 * {@code :quit} ends the session.
 */
public final class ReplMode implements Mode {
    @Override
    public int execute(String[] args, BufferedReader in, PrintStream out, PrintStream err) {
        new PromptShell(in, out).run();
        return 0;
    }
}
