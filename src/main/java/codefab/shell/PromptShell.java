package codefab.shell;

import codefab.CodeFabSession;
import codefab.RunResult;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

public final class PromptShell {

    private static final String PRIMARY_PROMPT = "> ";
    private static final String CONTINUATION_PROMPT = "....... > ";
    private static final String BANNER = "CodeFab REPL. Type 'exit' to quit.";

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
        boolean pendingElse = false;
        try {
            while (true) {
                out.print(buffer.isEmpty() && !pendingElse ? PRIMARY_PROMPT : CONTINUATION_PROMPT);
                out.flush();

                String line = in.readLine();
                if (line == null) break;

                String trimmed = line.trim();

                // 명령어 처리 (버퍼가 비어있고 pendingElse 아닐 때만)
                if (buffer.isEmpty() && !pendingElse && isCommand(trimmed)) {
                    if (handleCommand(trimmed)) break;
                    continue;
                }

                if (pendingElse) {
                    if (trimmed.isEmpty()) {
                        // 빈 줄 → if 블록 그대로 실행
                        execute(buffer.toString());
                        buffer.setLength(0);
                        pendingElse = false;
                        continue;
                    }
                    if (trimmed.startsWith("else")) {
                        // else 절 → 누적 후 일반 멀티라인으로 복귀
                        buffer.append(line).append('\n');
                        pendingElse = false;
                        // isComplete() 검사로 계속 진행
                    } else {
                        // else가 아닌 새 구문 → if 블록 먼저 실행, 새 줄 이어받기
                        execute(buffer.toString());
                        buffer.setLength(0);
                        pendingElse = false;
                        buffer.append(line).append('\n');
                    }
                } else {
                    buffer.append(line).append('\n');
                }

                if (!isComplete(buffer.toString())) continue;

                if (isPendingElse(buffer.toString())) {
                    pendingElse = true;
                    continue;
                }

                execute(buffer.toString());
                buffer.setLength(0);
            }
        } catch (IOException e) {
            out.println("I/O error: " + e.getMessage());
        }
    }

    private boolean isCommand(String trimmed) {
        return trimmed.equals("exit") || trimmed.equals("quit")
                || trimmed.startsWith(":");
    }

    private boolean handleCommand(String command) {
        switch (command) {
            case "exit":
            case "quit":
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

    /**
     * isComplete()가 true인 버퍼에서 최상위 레벨의 if 블록이 else 없이
     * 끝난 경우 true를 반환한다. 문자열·주석 내 if/else는 무시한다.
     *
     * 판별: braceDepth==0 조건에서 if/else 키워드를 카운트.
     * topLevelIfCount > topLevelElseCount 이면 else 대기 필요.
     */
    public static boolean isPendingElse(String source) {
        if (!isComplete(source)) return false;

        int ifCount = 0;
        int elseCount = 0;
        int braceDepth = 0;
        boolean inString = false;
        boolean inComment = false;
        char[] chars = source.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (inComment) {
                if (c == '\n') inComment = false;
                continue;
            }
            if (inString) {
                if (c == '"') inString = false;
                continue;
            }

            if (c == '"') { inString = true; continue; }
            if (c == '/' && i + 1 < chars.length && chars[i + 1] == '/') {
                inComment = true;
                continue;
            }

            if (c == '{') { braceDepth++; continue; }
            if (c == '}') { braceDepth--; continue; }

            // 최상위 레벨에서만 키워드 카운트
            if (braceDepth == 0 && isAlpha(c)) {
                int start = i;
                while (i < chars.length && isAlphaNumeric(chars[i])) i++;
                String word = source.substring(start, i);
                i--; // for 루프 i++ 보정
                if (word.equals("if")) ifCount++;
                else if (word.equals("else")) elseCount++;
            }
        }

        return ifCount > elseCount;
    }

    public static boolean isBalanced(String source) {
        int parens = 0;
        int braces = 0;
        int brackets = 0;
        boolean inString = false;
        boolean inComment = false;
        char[] chars = source.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inComment) {
                if (c == '\n') inComment = false;
                continue;
            }
            if (inString) {
                if (c == '"') inString = false;
                continue;
            }
            switch (c) {
                case '"':   inString = true; break;
                case '/':
                    if (i + 1 < chars.length && chars[i + 1] == '/') inComment = true;
                    break;
                case '(':   parens++; break;
                case ')':   parens--; break;
                case '{':   braces++; break;
                case '}':   braces--; break;
                case '[':   brackets++; break;
                case ']':   brackets--; break;
                default:    break;
            }
        }
        return !inString && parens <= 0 && braces <= 0 && brackets <= 0;
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isAlphaNumeric(char c) {
        return isAlpha(c) || (c >= '0' && c <= '9');
    }
}
