package codefab.shell;

import java.io.BufferedReader;
import java.io.PrintStream;

/**
 * 셸 실행 모드 전략 (GoF Strategy). {@link Main}이 인자에 따라 구현체를 고르고
 * {@link #execute}를 호출한다. 새 모드는 구현체 추가만으로 끼워 넣을 수 있다.
 *
 * @return 프로세스 종료 코드 (0=정상). {@code System.exit} 호출은 {@link Main#main}이
 *         담당하므로 모드는 코드만 반환해 테스트 가능하다.
 */
public interface Mode {
    int execute(String[] args, BufferedReader in, PrintStream out, PrintStream err);
}
