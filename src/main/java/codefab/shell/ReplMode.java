package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

/**
 * 인자 없음 → 대화형 REPL({@link PromptShell})을 실행한다.
 */
public final class ReplMode implements Mode {
    @Override
    public int execute(BufferedReader in, PrintStream out, PrintStream err) {
        new PromptShell(in, out).run();
        return 0;
    }
}
