package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

public interface Mode {
    int execute(BufferedReader in, PrintStream out, PrintStream err);
}
