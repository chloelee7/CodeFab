package codefab.shell;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ShellFiles {

    private ShellFiles() {
    }

    static String readUtf8(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    static void printReadError(PrintStream err, String path) {
        err.println("Error: cannot read file: " + path);
    }
}
