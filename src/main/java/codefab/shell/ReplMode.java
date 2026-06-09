package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

public final class ReplMode implements Mode {
    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        // 대화형이면 JLine(↑↓ 히스토리·wide-char 재그리기), 비대화형이면 BufferedReader 폴백.
        // catch는 두지 않는다: LineSource.close()는 throws 없는 no-op이라 close 예외 경로가
        // 없고, PromptShell.run()이 예외로 빠져나오는 경우(예상 못한 버그)를 가리면 안 된다.
        try (LineSource source = LineSources.forInteractive(in, out)) {
            new PromptShell(source, out).run();
        }
        return 0;
    }
}
