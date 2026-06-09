package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

public final class DebugMode implements Mode {

    private final String path;

    public DebugMode(String path) {
        this.path = path;
    }

    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        // 대화형이면 JLine(↑↓ 히스토리·wide-char 재그리기), 비대화형이면 BufferedReader 폴백.
        // catch는 두지 않는다: LineSource.close()는 throws 없는 no-op이라 close 예외 경로가
        // 없고, DebugShell.run()이 의도적으로 전파하는 디버거-버그 예외를 삼키면 안 된다.
        try (LineSource source = LineSources.forInteractive(in, out)) {
            new DebugShell(source, out, err, path).run();
        }
        return 0;
    }
}
