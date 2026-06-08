package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

public final class ReplMode implements Mode {
    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        new PromptShell(in, out).run();
        return 0;
    }
}
