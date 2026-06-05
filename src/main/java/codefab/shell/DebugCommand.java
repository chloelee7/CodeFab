package codefab.shell;

/**
 * 디버거의 한 명령(GoF Command). {@link DebugShell}이 이름→명령 레지스트리를 두고,
 * {@code >} 프롬프트에 입력된 줄의 첫 토큰으로 매칭되는 명령에 디스패치한다.
 *
 * <p>명령은 디버거 상태를 바꾸거나(브레이크포인트 설정, 워치 등록, 한 스텝 실행 …)
 * 출력을 낸다. 반환값은 명령 루프를 계속 돌릴지를 뜻한다.
 */
@FunctionalInterface
public interface DebugCommand {
    /**
     * @param shell    대상 디버그 셸
     * @param argument 명령어 뒤 텍스트(예: {@code break 7}의 "7"). 없으면 빈 문자열.
     * @return 프롬프트 루프를 계속하려면 {@code true}, 종료하려면 {@code false}.
     */
    boolean execute(DebugShell shell, String argument);
}
