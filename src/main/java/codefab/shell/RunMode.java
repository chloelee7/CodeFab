package codefab.shell;

import codefab.CodeFab;
import codefab.RunResult;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

public final class RunMode implements Mode {

    private static final int EX_DATA_ERR = 65;
    private static final int EX_NO_INPUT = 66;

    private final String path;

    public RunMode(String path) {
        this.path = path;
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

        RunResult result = new CodeFab().run(source);
        for (String line : result.output()) {
            out.println(line);
        }
        for (Diagnostic diagnostic : result.diagnostics()) {
            err.println(diagnostic.render());
        }
        return result.success() ? 0 : EX_DATA_ERR;
    }
}
