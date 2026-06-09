package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

/**
 * {@link LineSource} 팩토리. 환경에 맞는 구현을 선택한다(스펙 07).
 */
public final class LineSources {

    private LineSources() {}

    /**
     * 대화형 터미널이면 {@link JLineLineSource}, 그 외(비대화형·dumb·headless·생성 예외)
     * 전부 {@link BufferedLineSource}로 폴백한다.
     *
     * <p>대화형 판별은 {@code System.console() != null}이다. JLine Terminal/LineReader
     * 생성 중 어떤 예외(또는 Error)도 새어나가지 않게 감싸 항상 폴백을 보장한다 —
     * 비대화형(테스트/파이프) 경로는 기존 readLine 동작과 바이트 동일해야 하므로
     * JLine이 절대 활성화되지 않는다.
     */
    public static LineSource forInteractive(BufferedReader in, PrintStream out) {
        if (System.console() != null) {
            try {
                return JLineLineSource.create();
            } catch (Throwable ignored) {
                // JLine 생성 실패 — 폴백한다.
            }
        }
        return new BufferedLineSource(in, out);
    }
}
