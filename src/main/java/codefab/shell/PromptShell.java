package codefab.shell;

import codefab.CodeFabSession;
import codefab.RunResult;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.PrintStream;

/**
 * Interactive REPL. It reads input line by line and accumulates a multi-line buffer until the input
 * looks complete — that is, all parentheses and braces are balanced and the input ends in a way
 * that can stand on its own (a semicolon or a closing brace). The buffer is then run through a
 * single persistent {@link CodeFabSession} so variables survive between inputs.
 */
public final class PromptShell {

    private final BufferedReader in;
    private final PrintStream out;
    private final CodeFabSession session = new CodeFabSession();

    public PromptShell(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public void run() {
        out.println("CodeFab REPL. Type :exit to quit.");
        StringBuilder buffer = new StringBuilder();
        try {
            while (true) {
                out.print(buffer.length() == 0 ? "codefab> " : "....... > ");
                out.flush();

                String line = in.readLine();
                if (line == null) {
                    break; // EOF (e.g. piped input or Ctrl-D)
                }

                String trimmed = line.trim();
                if (buffer.length() == 0 && isCommand(trimmed)) {
                    if (handleCommand(trimmed)) {
                        break;
                    }
                    continue;
                }

                buffer.append(line).append('\n');
                if (!isComplete(buffer.toString())) {
                    continue; // keep reading until the statement/block closes
                }

                execute(buffer.toString());
                buffer.setLength(0);
            }
        } catch (java.io.IOException e) {
            out.println("I/O error: " + e.getMessage());
        }
    }

    private boolean isCommand(String trimmed) {
        return trimmed.startsWith(":");
    }

    /**
     * @return true if the shell should exit.
     */
    private boolean handleCommand(String command) {
        switch (command) {
            case ":exit":
            case ":quit":
                return true;
            case ":env":
                // Re-run a harmless no-op so users at least get a prompt back;
                // a full environment dump is intentionally out of scope.
                out.println("(environment inspection not supported)");
                return false;
            default:
                out.println("Unknown command: " + command);
                return false;
        }
    }

    private void execute(String source) {
        RunResult result = session.run(source);
        for (String outputLine : result.output()) {
            out.println(outputLine);
        }
        for (Diagnostic diagnostic : result.diagnostics()) {
            out.println(diagnostic.render());
        }
    }

    /**
     * Heuristic completeness check: balanced brackets and, when there is any content, an ending
     * that closes a statement or block. Unbalanced or dangling input keeps the buffer open for
     * another line.
     */
    public static boolean isComplete(String source) {
        int parens = 0;
        int braces = 0;
        boolean inString = false;
        boolean inComment = false;
        char[] chars = source.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inComment) {
                if (c == '\n') {
                    inComment = false;
                }
                continue;
            }
            if (inString) {
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"':
                    inString = true;
                    break;
                case '/':
                    if (i + 1 < chars.length && chars[i + 1] == '/') {
                        inComment = true;
                    }
                    break;
                case '(':
                    parens++;
                    break;
                case ')':
                    parens--;
                    break;
                case '{':
                    braces++;
                    break;
                case '}':
                    braces--;
                    break;
                default:
                    break;
            }
        }
        if (inString || parens > 0 || braces > 0) {
            return false;
        }

        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        // A complete chunk ends at a statement terminator or a closed block.
        return trimmed.endsWith(";") || trimmed.endsWith("}");
    }
}
