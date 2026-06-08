package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

public final class DebugMode implements Mode {

    private final String path;

    public DebugMode(String path) {
        this.path = path;
    }

    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        new DebugShell(in, out, err, path).run();
        return 0;
    }
}
