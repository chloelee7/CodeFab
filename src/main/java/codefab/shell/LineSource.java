package codefab.shell;

import java.io.IOException;

/**
 * 한 줄 입력 추상화. 프롬프트를 표시하고 한 줄을 읽는다.
 *
 * <p>스펙 07: 셸이 cooked-mode {@code BufferedReader.readLine()} 대신 이 추상화를 통해
 * 입력을 읽도록 하여, 대화형 환경에서는 JLine 라인 편집기(히스토리·wide-char 재그리기)를,
 * 비대화형(파이프/파일/테스트)에서는 기존 {@code BufferedReader} 경로를 사용한다.
 */
public interface LineSource extends AutoCloseable {

    /** prompt를 표시하고 한 줄을 읽는다. EOF면 null. */
    String readLine(String prompt) throws IOException;

    @Override
    default void close() {}
}
