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
     * <p>대화형 판별은 {@code System.console() != null}이다. 이는 stdin <b>과</b> stdout이
     * <b>둘 다</b> tty여야 true이므로, stdin은 터미널이지만 stdout만 리다이렉트된 경우
     * (예: {@code debug ... > log.txt})에도 {@link BufferedLineSource}로 폴백한다(안전한 쪽).
     * JLine Terminal/LineReader 생성 중 어떤 예외(또는 Error)도 새어나가지 않게 감싸 항상
     * 폴백을 보장한다 — 비대화형(테스트/파이프) 경로는 기존 readLine 동작과 바이트 동일해야
     * 하므로 JLine이 절대 활성화되지 않는다.
     *
     * <p>인자 사용: 폴백 경로({@link BufferedLineSource})는 {@code in}으로 읽고 {@code out}에
     * 프롬프트를 쓴다. JLine 경로는 {@code TerminalBuilder.system(true)}로 {@code System.in}을
     * 직접 소유하므로 {@code in}을 쓰지 않으며, {@code out}은 프롬프트 출력이 아니라
     * readLine 직전 flush(출력 순서 고정)에만 쓴다.
     */
    public static LineSource forInteractive(BufferedReader in, PrintStream out) {
        if (System.console() != null) {
            try {
                return JLineLineSource.create(out);
            } catch (Throwable ignored) {
                // JLine 생성 실패 — 폴백한다.
            }
        }
        return new BufferedLineSource(in, out);
    }
}
