---
name: executor-evaluation
description: "트리워킹 인터프리터의 Executor 유닛(Environment + AST 평가) 구현. AST를 DFS로 실행, 스코프별 Environment 운용, 산술·비교·논리·단항 연산 의미, 진리값, 출력 포맷팅, 런타임 오류 진단(미정의 변수·타입 오류·0 나눗셈) 작업에 사용. 새 연산자·값 타입·런타임 의미 추가, Environment 스코프 체이닝 작업에도 사용. CodeFab 인터프리터의 executor-engineer가 사용. 실행기/평가/런타임/Environment 작업이면 이 스킬을 사용할 것."
---

# Executor Evaluation — Environment · 평가

AST를 DFS로 실행하여 부수효과(출력, 변수 변경)를 만든다. 런타임 오류를 진단한다. **파싱하지 않고, 정적 검사도 하지 않는다.** Checker가 통과시킨 AST만 실행된다는 전제 위에서 동작한다.

## 작업 시작 전

`references/shared-contracts.md`에서 방문자 인터페이스, `OutputSink`, `InterpreterRuntimeError`, 고정 런타임 메시지를 확인한다.

## 값 타입

호스트 타입에 직접 매핑한다: 숫자=`Double`, 문자열=`String`, 불리언=`Boolean`, nil=`null`. 별도 래퍼를 만들지 않는다(단순성).

## Environment — 스코프 체인

각 스코프는 하나의 `Environment`다. `enclosing` 참조로 체인을 이룬다. 변수 탐색은 현재 스코프에서 시작해 바깥으로 진행한다.

- `define(name, value)`: 현재 스코프에 바인딩 생성/덮어쓰기.
- `get(name)`: 현재에 없으면 enclosing으로 위임. 끝까지 없으면 `Undefined variable '<name>'.` 런타임 오류.
- `assign(name, value)`: 기존 바인딩을 찾아 갱신하되 **새 바인딩을 만들지 않는다**. 없으면 미정의 변수 오류. 이로써 내부 블록에서 외부 변수 대입이 외부를 갱신한다.

## 평가 의미

**산술 `+`**: Number+Number 또는 String+String만 허용. 혼합은 `Operands must be two numbers or two strings.` 오류. `-` `*` `/`는 두 피연산자가 숫자여야 한다(`Operands must be numbers.`). `/`에서 우변이 0이면 `Division by zero.`.

**비교** `> >= < <=`: 숫자만, 불리언 반환(`Operands must be numbers.`). **동등** `== !=`: 임의 값 비교, null-safe 동등(둘 다 nil이면 true).

**단항**: `-`는 숫자 요구(`Operand must be a number.`), `+`는 숫자 요구하고 같은 값 반환, `!`는 진리값의 논리 부정.

**진리값**: `false`와 nil만 거짓. 그 외 모두 참. (논리 연산자 `and`/`or`는 단축 평가하며 피연산자 값을 그대로 반환할 수 있다.)

## 출력 포맷팅

`OutputSink.print`로만 출력한다(`System.out` 직접 사용 금지). 포맷 규칙:
- 정수값 double은 `.0`을 떼고 출력: `5`와 `5.0` 모두 `5`, `3.14`는 `3.14`.
  ```java
  if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
  ```
- 불리언은 `true`/`false`, 문자열은 따옴표 없이 원문, nil은 `nil`.

## 문 실행

**Block**: 새 `Environment(현재)`를 만들어 진입하고, **try/finally로 이전 Environment를 항상 복원한다**(런타임 오류가 나도 복원). 이 복원이 빠지면 오류 후 스코프가 오염된다.

**For**: 자신의 Environment를 연다(초기화 변수 누출 방지). 초기화 1회 실행 → 매 반복 전 조건 검사(**조건이 null이면 참**) → 참이면 본문 실행 → 증가 실행. 마찬가지로 finally로 이전 Environment 복원.

**If**: 조건 진리값으로 분기. elseBranch는 nullable이므로 null 체크.

## 런타임 오류

`InterpreterRuntimeError(token, message)`를 던진다(토큰은 줄 번호용). Facade가 이를 잡아 Stage.RUNTIME 진단으로 변환하므로, Executor는 던지기만 하고 진단 리스트를 직접 만지지 않는다. 정적으로 보장되지 않는 모든 동적 실패(타입, 미정의, 0 나눗셈)는 진단이지 호스트 예외가 아니다.

## 출력

`codefab.executor`에 Environment, Executor. Executor 생성자는 `OutputSink`와 전역 `Environment`를 주입받는다. `Expr.Visitor<Object>`(값 반환)와 `Stmt.Visitor<Void>`를 구현한다.
