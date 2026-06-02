---
name: checker-analysis
description: "트리워킹 인터프리터의 Checker 유닛(실행 전 정적 의미 분석) 구현. AST를 DFS로 순회하며 스코프 스택 기반으로 변수 중복 선언, 자기 초기화식에서의 변수 읽기 등 정적 오류를 진단. 변수 해석(resolution), 스코프 규칙, 셰도잉 규칙, DECLARED/DEFINED 상태 추적 작업에 사용. CodeFab 인터프리터의 checker-engineer가 사용. 체커/정적 분석/스코프/의미 검사 작업이면 이 스킬을 사용할 것."
---

# Checker Analysis — 정적 의미 분석

AST를 실행 전에 DFS로 순회하며 의미상 정적 오류를 진단한다. **코드를 실행하지 않는다.** 구문 오류는 Parser, 런타임 오류는 Executor의 책임이며, Checker는 정적 오류 2종만 다룬다. 체커가 오류를 보고하면 Executor는 실행되지 않아야 하므로(Facade가 보장), 체커의 정확성이 곧 실행 안전성이다.

## 작업 시작 전

`references/shared-contracts.md`에서 Expr/Stmt 방문자 인터페이스와 고정 진단 문자열을 확인한다.

## 스코프 모델

`Deque<Map<String, VarState>>`로 스코프 스택을 운용한다. `VarState`는 두 상태를 구분한다:

- **DECLARED**: 이름이 선언되었으나 초기화식이 아직 검사되지 않음.
- **DEFINED**: 초기화까지 끝나 안전하게 읽을 수 있음.

이 구분이 "자기 초기화식에서 읽기" 오류를 잡는 핵심이다. `var a = a;`에서 우변을 검사하는 순간 `a`는 DECLARED 상태이므로, 그 시점의 읽기는 잘못된 참조다.

## 방문 규칙

**VarStmt 방문 순서가 중요하다:**
1. `declare(name)` — 현재 스코프에 이름을 DECLARED로 넣는다. 이미 있으면 `Already a variable with this name in this scope.` 보고.
2. 초기화식이 있으면 `resolve(initializer)` — 이 시점에 이름은 아직 DECLARED.
3. `define(name)` — 이름을 DEFINED로 승격.

**Variable 방문:** 현재 스코프(`scopes.peek()`)에 이 이름이 **DECLARED 상태로** 있으면 `Can't read local variable in initializer.` 보고. 이 검사는 가장 안쪽 스코프만 본다 — 외부에 같은 이름이 DEFINED로 있어도, 안쪽이 DECLARED면 그 반쪽 선언을 가리키므로 오류다(셰도잉 자기 초기화도 오류).

**Block 방문:** `beginScope()` → 내부 문 resolve → `endScope()`. 중첩 스코프 셰도잉은 허용된다(다른 스코프이므로 중복이 아니다).

**For 방문:** 루프 자신의 스코프를 연다(`beginScope`/`endScope`로 감싼다). 초기화·조건·증가·본문을 차례로 resolve한다. 이로써 루프 초기화 변수가 바깥으로 새지 않는다.

**If/Print/ExpressionStmt:** 하위 식·문을 resolve할 뿐 스코프를 새로 열지 않는다. **elseBranch는 nullable이므로 null 체크 후 resolve한다.**

## 스코프 일관성

전역도 스코프 스택의 한 프레임으로 다룬다(`check()` 시작에서 `beginScope`). 그래야 전역 중복 선언도 같은 규칙으로 일관되게 잡힌다. 단, REPL에서는 입력마다 새 Checker를 쓰므로 입력 간 전역 재선언은 중복이 아니다(이 정책은 Facade/세션이 결정).

## 진단

오류는 `Diagnostic`(Stage.CHECKER, 토큰 줄 번호)로 수집한다. 던지지 않는다 — 한 번의 순회로 여러 정적 오류를 모두 모은다. 고정 문자열을 정확히 포함시킨다.

## 무엇을 추가하지 않는가

타입 검사, 도달 불가 코드, 미사용 변수 등은 스펙에 없으면 추가하지 않는다(오버엔지니어링 금지). 새 정적 규칙을 추가할 때는 먼저 test-author와 합의하고 계약/테스트를 갱신한다.

## 출력

`codefab.checker.Checker`. 생성자에 진단 리스트를 주입받고, `check(List<Stmt>)`를 공개 진입점으로 둔다. `Expr.Visitor<Void>`와 `Stmt.Visitor<Void>`를 구현한다.
