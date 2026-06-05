---
name: checker-analysis
description: "트리워킹 인터프리터의 Checker 유닛(실행 전 정적 의미 분석 + 리졸버 + 옵티마이저) 구현. AST를 DFS로 순회하며 스코프 스택 기반으로 변수 중복 선언, 자기 초기화식 변수 읽기, 함수 외부 return, 파라미터 이름 중복 등 정적 오류를 진단. 또한 정적 바인딩 거리(distance) 계산과 상수 폴딩(수식 합치기) 실행 전 최적화를 수행. 변수 해석(resolution), 스코프 규칙, 셰도잉, DECLARED/DEFINED 상태 추적, FunctionType 추적, distance 맵 산출, 상수 접기 작업에 사용. CodeFab 인터프리터의 checker-engineer가 사용. 체커/정적 분석/스코프/의미 검사/리졸버/distance/상수 폴딩/최적화 작업이면 이 스킬을 사용할 것."
---

# Checker Analysis — 정적 의미 분석 + 리졸버 + 옵티마이저

AST를 실행 전에 DFS로 순회하며 (1) 의미상 정적 오류를 진단하고, (2) 변수 참조 거리(distance)를 해석하며, (3) 상수 부분식을 폴딩한다. **코드를 실행하지 않는다.** 구문 오류는 Parser, 런타임 오류는 Executor의 책임이다. 체커가 오류를 보고하면 Executor는 실행되지 않아야 하므로(Facade가 보장), 체커의 정확성이 곧 실행 안전성이다. 또한 체커가 산출한 distance 맵과 폴딩된 AST가 곧 실행 성능이다.

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

## 함수 관련 정적 오류

함수 기능 추가로 Checker의 책임이 4종으로 확장된다(계약 §4).

**FunctionStmt 방문:**
1. `declare(name)` + `define(name)` — 함수 이름을 현재 스코프에 선언(중복이면 `Already a variable with this name in this scope.`). 재귀 호출이 가능하도록 본문 resolve 전에 define한다.
2. 함수 본문 스코프를 연다(`beginScope`).
3. 각 파라미터를 `declare`+`define` — **파라미터 이름 중복은 기존 declare 로직이 자동으로 잡는다**(`Already a variable with this name in this scope.`).
4. `currentFunction` 상태를 FUNCTION으로 바꾼 뒤 본문을 resolve(끝나면 복원). NONE→FUNCTION 전이로 "함수 안인지"를 추적한다.
5. `endScope`.

**ReturnStmt 방문:** `currentFunction == NONE`이면 `Can't return from top-level code.` 보고. value가 있으면 resolve(nullable — `return;`은 null).

**Call 방문:** callee와 각 argument를 resolve(인자 개수 검사는 런타임 책임이므로 여기서 하지 않는다). **Index/IndexSet 방문:** target·index(·value)를 resolve.

> 함수 외부 return·파라미터 중복은 **정적(CHECKER)**, 함수 아닌 대상 호출·인자 개수 불일치는 **동적(RUNTIME)**이다. 이 경계를 지킨다.

## 정적 바인딩 — 거리(distance) 해석

스코프 스택의 각 프레임이 한 블록의 변수 집합이다. `Variable`/`Assign`을 방문할 때, 스택을 안쪽에서 바깥쪽으로 훑어 이름이 처음 나타나는 프레임까지의 **거리**를 센다(0 = 현재 스코프, 1 = 한 단계 바깥…).

- 찾으면 `locals.put(expr, distance)` — 식 노드를 키로 거리를 기록한다.
- 끝까지 못 찾으면 **전역**으로 간주하고 맵에 넣지 않는다(Executor가 전역 조회로 폴백).
- `locals`는 `Map<Expr,Integer>` (IdentityHashMap 권장 — 노드 동일성 기준).

이 맵이 Executor의 O(1) 접근 근거다(계약 §9-1). 셰도잉이 있어도 "가장 안쪽에서 처음 만나는 선언"까지의 거리가 정답이다.

## 상수 폴딩 — 수식 합치기

AST를 후위 순회하며, **런타임 이전에 100% 값이 확정되는 부분식**을 단일 `Literal`로 교체한 새 트리를 만든다(계약 §9-2). 노드는 불변이므로 수정하지 말고 새 노드로 치환한다.

- 폴딩 가능: `Literal`, 또는 피연산자가 모두 상수로 접힌 `Binary`/`Unary`/`Grouping`/`Logical`(number 산술·비교·`%`, boolean 논리, 단항 `-`/`!`). 문자열 상수 `+`도 재량 폴딩.
- 폴딩 불가(차단): `Variable`/`Assign`/`Call`/`Index`/`IndexSet`이 들어간 부분식. 상위는 부분만 접는다.
- **0 나누기 안전장치**: 폴딩 중 `/ 0` 또는 `% 0`이면 그 부분식은 **접지 말고 원본 유지**(런타임이 `Division by zero.`를 보고해야 의미가 보존된다). 폴딩이 런타임 의미를 바꾸면 안 된다.

폴딩과 distance 해석은 한 패스로 합쳐도 되고 분리해도 된다. 분리 시 폴딩을 먼저 하고(트리 모양 확정) 그 트리에 대해 distance를 매긴다.

## 무엇을 추가하지 않는가

타입 검사, 도달 불가 코드, 미사용 변수 등은 스펙에 없으면 추가하지 않는다(오버엔지니어링 금지). 인자 개수 검사를 Checker로 끌어오지 않는다(런타임 책임). 새 정적 규칙을 추가할 때는 먼저 test-author와 합의하고 계약/테스트를 갱신한다.

## 출력

`codefab.checker.Checker`. 생성자에 진단 리스트를 주입받고, `CheckResult check(List<Stmt>)`를 공개 진입점으로 둔다. `CheckResult`는 폴딩된 `List<Stmt> program()`과 `Map<Expr,Integer> locals()`를 보유한다(계약 §6). `Expr.Visitor<R>`와 `Stmt.Visitor<R>`를 구현한다. 진단은 기존처럼 주입 리스트에 누적, distance는 locals 맵에, 폴딩은 새 AST로.

GoF 패턴: 방문자(Visitor)로 순회, 옵티마이저는 AST→AST 변환(Interpreter 패턴의 전처리 패스).
