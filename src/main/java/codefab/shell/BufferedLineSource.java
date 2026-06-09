package codefab.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/**
 * {@link LineSource}의 기존 동작 1:1 구현. {@code out.print(prompt)} 후
 * {@code in.readLine()}을 위임한다. 비대화형(파이프/파일/테스트) 경로 전용으로,
 * LineSource 도입 전 셸의 입력 동작과 바이트 단위로 동일하다.
 */
public final class BufferedLineSource implements LineSource {

    private final BufferedReader in;
    private final PrintStream out;

    public BufferedLineSource(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public String readLine(String prompt) throws IOException {
        out.print(prompt);
        out.flush();
        return in.readLine();
    }
}
