package codefab.shell;

import codefab.CodeFabSession;
import codefab.RunResult;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.PrintStream;

/**
 * Interactive REPL with Python-style block input. It reads input line by line
 * and accumulates a multi-line buffer.
 *
 * <p>Simple statements (ending in {@code ;}) and self-completing blocks
 * ({@code for}/{@code while}/bare {@code {}}/a closed {@code if}/{@code else})
 * run immediately on Enter when their brackets balance. The single exception is
 * an {@code if} block with no {@code else} yet attached: because a trailing
 * {@code else} could still follow, such a buffer is <em>not</em> executed when
 * its brace closes. Instead it keeps accumulating — including any trailing
 * lines — until a blank line (empty or whitespace only) is entered, at which
 * point the whole pending buffer runs. This mirrors the blank-line flush of a
 * Python interactive block. See {@link #awaitsElse}.
 *
 * <p>Brace/paren balance and block detection both ignore braces inside strings
 * and {@code //} comments. The buffer is run through a single persistent
 * {@link CodeFabSession} so variables survive between inputs.
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
        out.println("CodeFab REPL. Type exit or quit to leave.");
        StringBuilder buffer = new StringBuilder();
        try {
            while (true) {
                out.print(buffer.length() == 0 ? "codefab> " : "....... > ");
                out.flush();

                String line = in.readLine();
                if (line == null) break; // EOF: never flush a pending buffer.

                String trimmed = line.trim();

                // Commands are only honored when no input is pending.
                if (buffer.length() == 0 && isCommand(trimmed)) {
                    if (handleCommand(trimmed)) break;
                    continue;
                }

                if (trimmed.isEmpty()) {
                    // A blank line flushes the buffer only if it is complete;
                    // an empty buffer or still-incomplete input is left alone.
                    if (buffer.length() == 0) continue;
                    if (isComplete(buffer.toString())) {
                        execute(buffer.toString());
                        buffer.setLength(0);
                    }
                    continue;
                }

                buffer.append(line).append('\n');
                String pending = buffer.toString();
                if (!isBalanced(pending)) {
                    continue; // brackets still open — keep reading.
                }
                if (awaitsElse(pending)) {
                    // An if-block with no `else` yet attached: a trailing `else`
                    // may still follow, so wait for a blank line (or `else`) to
                    // flush rather than running on the closing brace.
                    continue;
                }
                // Balanced and not awaiting an `else`: a closed block (for/while/
                // bare/if-else) or a `;`-terminated simple statement runs now.
                String tail = pending.trim();
                if (tail.endsWith(";") || tail.endsWith("}")) {
                    execute(pending);
                    buffer.setLength(0);
                }
                // Otherwise (balanced, no terminator) keep accumulating.
            }
        } catch (java.io.IOException e) {
            out.println("I/O error: " + e.getMessage());
        }
    }

    private boolean isCommand(String trimmed) {
        // ':'-prefixed shell commands, plus bare exit/quit per the prompt-mode spec.
        return trimmed.startsWith(":") || trimmed.equals("exit") || trimmed.equals("quit");
    }

    /** @return true if the shell should exit. */
    private boolean handleCommand(String command) {
        switch (command) {
            case "exit":
            case "quit":
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
     * Heuristic completeness check: balanced brackets and, when there is any
     * content, an ending that closes a statement or block. Unbalanced or
     * dangling input keeps the buffer open for another line.
     *
     * <p>Signature and semantics are unchanged; callers (and tests) may rely on
     * it exactly as before.
     */
    public static boolean isComplete(String source) {
        Scan scan = scan(source);
        if (scan.inString || scan.parens > 0 || scan.braces > 0) return false;

        String trimmed = source.trim();
        if (trimmed.isEmpty()) return false;
        // A complete chunk ends at a statement terminator or a closed block.
        return trimmed.endsWith(";") || trimmed.endsWith("}");
    }

    /**
     * @return true when parens and braces are balanced and no string is left
     * open — i.e. the brackets/quotes do not require another line.
     */
    static boolean isBalanced(String source) {
        Scan scan = scan(source);
        return !scan.inString && scan.parens <= 0 && scan.braces <= 0;
    }

    /**
     * Decides whether a balanced buffer is an {@code if}-block that has not yet
     * grown an {@code else} — the one case where the REPL must wait for a blank
     * line (or a trailing {@code else}) instead of running on the closing brace.
     *
     * <p>Algorithm (string/{@code //}-comment aware throughout):
     * <ol>
     *   <li>If the trimmed source does not end in {@code '}'}, it is not a
     *       closed block — return false (a {@code ;}-terminated statement, or
     *       still-incomplete input, never awaits an else).</li>
     *   <li>Find the matching {@code '{'} of the final top-level {@code '}'} by
     *       tracking brace depth; the partner is the {@code '{'} that first
     *       raised depth to the level the final {@code '}'} closes.</li>
     *   <li>Inspect the non-space text immediately before that {@code '{'}:
     *       if it is {@code else} → the if-else is complete (false); if it is a
     *       {@code )} whose matching {@code (} is preceded by the {@code if}
     *       keyword (and not an {@code else if}) → an else-less if (true);
     *       otherwise ({@code for}/{@code while}/bare block) → false.</li>
     * </ol>
     * Keyword matches respect word boundaries so identifiers like {@code elseif}
     * are not mistaken for the {@code else}/{@code if} keywords.
     */
    static boolean awaitsElse(String source) {
        char[] chars = source.toCharArray();
        boolean[] code = codeMask(chars); // true where the char is real code

        // Walk every top-level (depth-0) brace block. A buffer "awaits else" if
        // ANY such block is an else-less if whose closing `}` is not followed by
        // an `else` keyword — even if later statements were appended after it, so
        // that trailing input keeps accumulating until a blank line flushes.
        int depth = 0;
        int blockOpen = -1; // '{' index of the current depth-0 block
        for (int i = 0; i < chars.length; i++) {
            if (!code[i]) continue;
            char c = chars[i];
            if (c == '{') {
                if (depth == 0) blockOpen = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && blockOpen >= 0) {
                    if (isElselessIf(chars, code, blockOpen, i)) return true;
                    blockOpen = -1;
                }
                if (depth < 0) depth = 0; // tolerate stray '}' defensively
            }
        }
        return false;
    }

    /**
     * @return true when the top-level block spanning {@code open}..{@code close}
     * is an {@code if (...) { ... }} that has no {@code else} attached after its
     * closing brace. {@code for}/{@code while}/{@code else}-blocks and bare
     * blocks return false.
     */
    private static boolean isElselessIf(char[] chars, boolean[] code, int open, int close) {
        // Text just before the block's '{'.
        int end = prevNonSpace(chars, code, open - 1);
        if (end < 0) return false;

        // `else { ... }` → the if-else is complete, not awaiting an else.
        if (keywordEndsAt(chars, code, end, "else")) return false;

        // Must look like `if (...) {`.
        if (chars[end] != ')') return false;
        int openParen = matchOpenParen(chars, code, end);
        if (openParen <= 0) return false;
        int kw = prevNonSpace(chars, code, openParen - 1);
        if (kw < 0 || !keywordEndsAt(chars, code, kw, "if")) return false;

        // If an `else` keyword already follows the closing '}', it is complete.
        int after = nextNonSpace(chars, code, close + 1);
        if (after >= 0 && keywordStartsAt(chars, code, after, "else")) return false;

        return true;
    }

    /** @return index of the next code char that is not whitespace, or -1. */
    private static int nextNonSpace(char[] chars, boolean[] code, int from) {
        for (int i = from; i < chars.length; i++) {
            if (code[i] && !Character.isWhitespace(chars[i])) return i;
        }
        return -1;
    }

    /**
     * @return true when {@code keyword} starts exactly at index {@code start} in
     * the code text, with a word boundary on both sides.
     */
    private static boolean keywordStartsAt(char[] chars, boolean[] code, int start, String keyword) {
        if (start < 0) return false;
        return keywordEndsAt(chars, code, start + keyword.length() - 1, keyword);
    }

    /** @return index of the matching {@code '('} for the {@code ')'} at {@code close}, or -1. */
    private static int matchOpenParen(char[] chars, boolean[] code, int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            if (!code[i]) continue;
            if (chars[i] == ')') depth++;
            else if (chars[i] == '(') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** @return index of the previous code char that is not whitespace, or -1. */
    private static int prevNonSpace(char[] chars, boolean[] code, int from) {
        for (int i = from; i >= 0; i--) {
            if (code[i] && !Character.isWhitespace(chars[i])) return i;
        }
        return -1;
    }

    /**
     * @return true when {@code keyword} ends exactly at index {@code end} in the
     * code text, with a word boundary on both sides (so {@code elseif} or
     * {@code ifx} do not match {@code else}/{@code if}).
     */
    private static boolean keywordEndsAt(char[] chars, boolean[] code, int end, String keyword) {
        if (end < 0) return false;
        int len = keyword.length();
        int start = end - len + 1;
        if (start < 0) return false;
        for (int i = 0; i < len; i++) {
            int idx = start + i;
            if (!code[idx] || chars[idx] != keyword.charAt(i)) return false;
        }
        // Boundary before the keyword: start of input or a non-identifier char.
        if (start > 0 && isIdentifierChar(chars[start - 1])) return false;
        // Boundary after the keyword: end of input or a non-identifier char, so
        // `elseif`/`ifx` are not mistaken for `else`/`if`.
        if (end + 1 < chars.length && isIdentifierChar(chars[end + 1])) return false;
        return true;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * @return a mask the length of {@code chars} where an entry is true iff that
     * character is real code (outside string literals and {@code //} comments).
     */
    private static boolean[] codeMask(char[] chars) {
        boolean[] code = new boolean[chars.length];
        boolean inString = false;
        boolean inComment = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inComment) {
                if (c == '\n') inComment = false;
                continue;
            }
            if (inString) {
                if (c == '"') inString = false;
                continue; // the char (incl. closing quote) is not bare code
            }
            if (c == '"') { inString = true; continue; }
            if (c == '/' && i + 1 < chars.length && chars[i + 1] == '/') {
                inComment = true;
                continue;
            }
            code[i] = true;
        }
        return code;
    }

    /** Result of a single string/comment-aware scan of the source. */
    private static final class Scan {
        int parens;
        int braces;
        boolean inString;
    }

    /**
     * Scans the source once, tracking string and {@code //} comment state, and
     * tallies bracket balance. Shared by {@link #isComplete} and
     * {@link #isBalanced} so they agree on what counts as code.
     */
    private static Scan scan(String source) {
        Scan scan = new Scan();
        boolean inComment = false;
        char[] chars = source.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inComment) {
                if (c == '\n') inComment = false;
                continue;
            }
            if (scan.inString) {
                if (c == '"') scan.inString = false;
                continue;
            }
            switch (c) {
                case '"': scan.inString = true; break;
                case '/':
                    if (i + 1 < chars.length && chars[i + 1] == '/') inComment = true;
                    break;
                case '(': scan.parens++; break;
                case ')': scan.parens--; break;
                case '{': scan.braces++; break;
                case '}': scan.braces--; break;
                default: break;
            }
        }
        return scan;
    }
}
