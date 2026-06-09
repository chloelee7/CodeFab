package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

public final class ReplMode implements Mode {
    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        // 대화형이면 JLine(↑↓ 히스토리·wide-char 재그리기), 비대화형이면 BufferedReader 폴백.
        try (LineSource source = LineSources.forInteractive(in, out)) {
            new PromptShell(source, out).run();
        } catch (Exception ignored) {
            // close 등에서 새는 예외는 무시한다(셸 종료 경로).
        }
        return 0;
    }
}
