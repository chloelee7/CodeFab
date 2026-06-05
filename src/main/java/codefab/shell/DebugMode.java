package codefab.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug mode: load a script and run it under the interactive {@link Debugger},
 * pausing at statement granularity. The debugger reads its commands from
 * {@code in} and prints to {@code out}, so a scripted session can drive it.
 *
 * <p>If the file cannot be read, behaves like {@link RunMode}: prints
 * {@code Could not read file '<path>'} and returns {@link RunMode#EXIT_NO_INPUT}.
 */
public final class DebugMode implements Mode {
    private final String path;

    public DebugMode(String path) {
        this.path = path;
    }

    @Override
    public int execute(String[] args, BufferedReader in, PrintStream out, PrintStream err) {
        String source;
        try {
            source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("Could not read file '" + path + "'");
            return RunMode.EXIT_NO_INPUT;
        }
        return new Debugger(path, source, in, out).run();
    }
}
