# 스펙: REPL 블록 입력 파이썬식 빈 줄 트리거 (부분 재실행)

## 요청
REPL 모드에서 if/else(및 모든 `{}` 블록) 멀티라인 입력을 파이썬 REPL처럼 처리.
블록이 닫힌 뒤에도 즉시 실행하지 않고, **빈 줄(연속 \n)** 이 입력돼야 누적 버퍼를 실행.

## 영향 범위
- 단일 유닛: shell (`shell/PromptShell.java`).
- 공유 계약(Token/AST/Diagnostic) **무변경**.

## 동작 규칙 (run 루프) — 개정: if만 빈 줄 트리거
**핵심:** `if`(아직 `else` 없는) 블록만 빈 줄 트리거 필요(뒤에 `else`가 올 수 있어 완결 판단 불가).
`for`/`while`/bare `{}`/`else`로 완결된 if-else 블록은 `}` 닫히면 **엔터 한 번에 즉시 실행**(후속 없음).

- 명령(`:exit`/`exit`/`quit` 등)은 버퍼 비었을 때만 처리.
- 빈 줄 입력:
  - 버퍼 비었으면 무시(continue).
  - 버퍼 차 있고 `isComplete` → 실행 후 리셋.
  - 그 외 계속 누적.
- 비빈 줄: 버퍼에 append 후
  - 괄호/중괄호 불균형 → 계속 읽기.
  - 균형 + **else 가능한 if 블록(`awaitsElse`)** → 즉시 실행 안 함. 빈 줄(또는 else 연속) 대기.
  - 균형 + `;` 또는 `}`로 끝 → 즉시 실행.
  - 그 외 → 계속.
- EOF: pending 버퍼 실행하지 않고 종료(break).

### `awaitsElse(source)` 판정 알고리즘
균형 잡힌 버퍼가 `}`로 끝날 때, 마지막 `}`의 짝 `{`를 찾는다(문자열/주석 무시). 그 `{` 직전의 텍스트를 본다:
- `else` 키워드 직후 → else 블록 = if-else 완결 → **즉시 실행**.
- `if (...)` (앞에 else 없음) → else 가능 → **빈 줄 대기**.
- `for`/`while`/그 외(bare 블록) → **즉시 실행**.

## 불변(깨지면 안 됨)
- `var a=5; var b=10; print a+b;` → 15 (단순문 즉시 실행).
- 진단 출력 동작.
- `isComplete` 정적 시그니처/의미 유지.

## 테스트 영향 (개정)
- `accumulatesMultiLineBlockUntilBraceCloses`: bare 블록 → 즉시 실행. 빈 줄 제거(원래대로).
- if(else 없음): 빈 줄 전 미실행 / 빈 줄 후 실행.
- if-else: 빈 줄 없이 `}` 닫히면 즉시 실행.
- for/while: `}` 닫히면 즉시 실행(빈 줄 불필요).

## 파이프라인 순서
test-author(테스트 갱신/추가) → shell-integrator(PromptShell 구현) → qa-verifier(./gradlew test)
