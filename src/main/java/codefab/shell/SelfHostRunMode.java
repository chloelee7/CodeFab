package codefab.shell;

import codefab.RunResult;
import codefab.SelfHostCodeFab;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SelfHostRunMode implements Mode {

    private static final int EX_DATA_ERR = 65;
    private static final int EX_NO_INPUT = 66;

    private final String path;

    public SelfHostRunMode(String path) {
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

        RunResult result = new SelfHostCodeFab().run(source);
        for (String line : result.output()) {
            out.println(line);
        }
        for (Diagnostic diagnostic : result.diagnostics()) {
            err.println(diagnostic.render());
        }
        return result.success() ? 0 : EX_DATA_ERR;
    }
}
