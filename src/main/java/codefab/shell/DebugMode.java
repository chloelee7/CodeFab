package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

/**
 * 디버그 모드: {@link DebugShell}로 스크립트를 단계 실행한다(step/break/watch/inspect).
 */
public final class DebugMode implements Mode {

    private final String path;

    public DebugMode(String path) {
        this.path = path;
    }

    @Override
    public int execute(String[] args, BufferedReader in, PrintStream out, PrintStream err) {
        new DebugShell(in, out, path).run();
        return 0;
    }
}
