package codefab.executor;

/**
 * 함수 return 제어 흐름 전용 예외. 사용자에게 노출되지 않는다.
 * 스택트레이스 생성을 비활성화해 성능 오버헤드를 제거한다.
 */
class ReturnException extends RuntimeException {
    final Object value;

    ReturnException(Object value) {
        super(null, null, false, false);
        this.value = value;
    }
}
