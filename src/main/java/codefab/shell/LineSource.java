package codefab.shell;

import java.io.IOException;

/**
 * 한 줄 입력 추상화. 프롬프트를 표시하고 한 줄을 읽는다.
 *
 * <p>스펙 07: 셸이 cooked-mode {@code BufferedReader.readLine()} 대신 이 추상화를 통해
 * 입력을 읽도록 하여, 대화형 환경에서는 JLine 라인 편집기(히스토리·wide-char 재그리기)를,
 * 비대화형(파이프/파일/테스트)에서는 기존 {@code BufferedReader} 경로를 사용한다.
 *
 * <p>소유권 규칙: {@code close()}는 <b>이 LineSource를 생성한 팩토리/호출자</b>(즉
 * {@code LineSources.forInteractive}를 try-with-resources로 감싸는 Mode)의 책임이다.
 * 셸({@code DebugShell}/{@code PromptShell})은 LineSource를 필드로 들고 쓰기만 할 뿐
 * 소유자가 아니므로 close하지 않는다. 셸의 stdin/stdout 자체도 LineSource가 소유하지
 * 않으므로 닫지 않는다(기본 구현이 no-op인 이유).
 */
public interface LineSource extends AutoCloseable {

    /** prompt를 표시하고 한 줄을 읽는다. EOF면 null. */
    String readLine(String prompt) throws IOException;

    /** {@inheritDoc} 기본 구현은 no-op이다(소유 자원이 없는 구현용). */
    @Override
    default void close() {}
}
