package codefab.shell;

import codefab.CodeFabSession;
import codefab.RunResult;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

public final class PromptShell {

    private static final String PRIMARY_PROMPT = "codefab> ";
    private static final String CONTINUATION_PROMPT = "....... > ";
    private static final String BANNER = "CodeFab REPL. Type :exit to quit.";

    private final BufferedReader in;
    private final PrintStream out;
    private final CodeFabSession session = new CodeFabSession();

    public PromptShell(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public void run() {
        out.println(BANNER);
        StringBuilder buffer = new StringBuilder();
        try {
            while (true) {
                prompt(buffer);

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
                    continue;
                }

                execute(buffer.toString());
                buffer.setLength(0);
            }
        } catch (IOException e) {
            out.println("I/O error: " + e.getMessage());
        }
    }

    private void prompt(StringBuilder buffer) {
        out.print(buffer.length() == 0 ? PRIMARY_PROMPT : CONTINUATION_PROMPT);
        out.flush();
    }

    private boolean isCommand(String trimmed) {
        return trimmed.startsWith(":");
    }

    private boolean handleCommand(String command) {
        switch (command) {
            case ":exit":
            case ":quit":
                return true;
            case ":env":
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

    public static boolean isComplete(String source) {
        if (!isBalanced(source)) {
            return false;
        }
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.endsWith(";") || trimmed.endsWith("}");
    }


    private static boolean isBalanced(String source) {
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
        return !inString && parens <= 0 && braces <= 0;
    }
}
