---
name: interpreter-tdd
description: "인터프리터/컴파일러 스테이지에 대한 JUnit 5 실패 테스트를 구현 전에 먼저 작성. 스캐너·파서·체커·실행기·REPL의 테스트 케이스 작성, 스펙을 테스트로 고정, TDD 선작성, 회귀 테스트 추가, 새 언어 기능의 기대 동작을 테스트로 명세할 때 사용. CodeFab 인터프리터 개발 시 test-author와 qa-verifier가 사용."
---

# Interpreter TDD — 스테이지별 실패 테스트 선작성

스펙을 실행 가능한 테스트로 고정하여, 구현이 충족해야 할 계약을 명확히 한다. 구현보다 먼저 테스트를 작성하므로, 처음에는 컴파일 실패하거나 실패하는 것이 정상이다. 테스트는 단언이 곧 스펙이 되도록 구체적으로 쓴다.

## 작업 시작 전

`references/shared-contracts.md`를 읽어 단언이 의존할 클래스 시그니처(`CodeFab.run`, `RunResult`, `Diagnostic.Stage` 등)와 고정 진단 문자열을 확인한다.

## 테스트 계층

두 계층으로 나눠 작성한다. 단위 테스트는 한 유닛을 직접 호출하고, 통합 테스트는 facade로 파이프라인 전체를 돌린다.

| 계층 | 대상 | 진입점 |
|------|------|--------|
| 단위 | Scanner, Parser, Checker | `new Scanner(src, diags)`, `new Parser(tokens, diags)`, `new Checker(diags).check(stmts)` → `CheckResult`(program/locals) |
| 통합 | 전체 파이프라인 | `new CodeFab().run(src)` → `RunResult` |
| REPL | 세션·셸 | `new CodeFabSession()`, `PromptShell` |

## 핵심 원칙

**System.out에 의존하지 않는다.** 출력은 주입된 수집기를 통해 검증한다. 통합 테스트는 `RunResult.output()`(List<String>)을 단언하고, 단위 테스트는 진단 리스트를 단언한다. 이렇게 해야 테스트가 병렬 실행과 환경에 영향받지 않는다.

**진단은 부분 문자열로 검사한다.** 파서가 메시지 뒤에 위치 정보를 덧붙일 수 있으므로 `equals`가 아니라 `contains`로 검사한다. 단계(Stage)까지 함께 단언하면 "체커 오류가 런타임 단계에서 잡히는" 류의 경계 버그를 잡는다.

```java
assertTrue(r.diagnostics().stream()
    .anyMatch(d -> d.stage == Diagnostic.Stage.RUNTIME
                && d.message.contains("Undefined variable 'x'.")));
```

**성공/실패를 둘 다 단언한다.** 정상 케이스는 `r.success()`가 true이고 출력이 기대와 일치함을, 오류 케이스는 `r.success()`가 false이고 기대 진단이 존재하며 **부수효과가 없음**(예: 체커 실패 시 출력이 비어 있음)을 단언한다.

## 무엇을 테스트로 고정하는가

스펙의 각 규칙을 최소 한 개의 단언으로 옮긴다. CodeFab의 경우:

- **연산/우선순위/진리값**: 산술 우선순위, 그룹화, 좌결합(`10-4-3`→3), 단항, 비교, 문자열 연결, 불리언.
- **출력 포맷**: 정수값 double은 `.0` 없이(`5`, `5.0`→`5`), 소수는 그대로(`3.14`), 불리언/문자열.
- **변수/스코프/셰도잉**: 전역·블록 스코프, 내부 셰도잉, 내부 블록에서 외부 변수 대입이 외부를 갱신, for 루프 변수 누출 안 됨.
- **제어 흐름**: if/else, dangling-else는 가장 가까운 if에 결합, for 루프 반복.
- **구문 오류**: 세미콜론 누락, 닫는 괄호 누락, 잘못된 대입 대상, 표현식 시작 불가.
- **정적 오류**: 같은 스코프 중복 선언, 자기 초기화식에서 읽기, 셰도잉은 허용.
- **런타임 오류**: 미정의 변수(읽기·대입), 타입 혼합 +, 단항 - 비숫자, 0 나눗셈, 비교 비숫자.
- **스캐너 진단**: 알 수 없는 문자, 미종료 문자열, 주석 무시, 줄 번호 추적.
- **REPL**: 입력 간 변수 유지, 멀티라인 블록 누적, 진단 후에도 세션 생존. **교차-run 함수 호출**(한 입력에서 `Func` 정의 → 다른 입력에서 호출, 재귀 포함)을 반드시 포함한다 — 단발 실행은 한 run에 모든 노드가 들어가 정적 바인딩 distance 수명 버그를 못 잡으므로 세션 테스트가 따로 필요(실제 발생 버그).
- **함수**: 선언+호출(`add(3,7)`→10), `return ;`→nil, 반환값 대입(`ret=add(1,2)`), 재귀(`fact(5)`→120). 오류: 함수 외부 return(CHECKER `Can't return from top-level code.`), 파라미터 중복(CHECKER `Already a variable...`), 함수 아닌 대상 호출(RUNTIME `Can only call functions.`), 인자 개수 불일치(RUNTIME `Expected <n> arguments but got <m>.`).
- **정적 배열**: `Array(3)` 생성·초기 null, 인덱스 읽기/쓰기, 식 인덱스(`arr[i-1]`). 오류: 범위 초과(`Array index out of bounds.`), 비숫자 인덱스(`Array index must be a number.`), 배열 아닌 대상(`Can only index arrays.`), 비숫자 크기(`Array size must be a number.`).
- **공장 제어 쉘**: 파일 모드(파일 부재 메시지, 런타임 오류 줄번호 포함), 디버그 모드(step/next/break/continue/breakpoints/remove, watch/unwatch/watches/inspect) — 스크립트된 입력으로 통합 테스트.

## 최적화 Test Double 검증 (계약 §9-3)

스펙은 두 최적화를 **Test Double로 검증**하라 명시한다. 동작 결과만이 아니라 "최적화가 실제로 일어났는지"를 단언한다.

- **정적 바인딩**: Environment의 `enclosing` 추적 횟수를 세는 스파이를 주입(또는 카운팅 서브클래스). 깊게 중첩된 스코프에서 변수 접근 시, 거리 기반 접근이 체인을 **거슬러 올라가지 않음**(스파이 카운트가 distance 이하)을 단언. distance 맵에 기대 거리가 들어 있는지도 직접 단언 가능(`CheckResult.locals()`).
- **상수 폴딩**: 연산 횟수를 세는 스파이(예: Binary 평가 카운터) 또는 폴딩 전/후 AST의 Binary 노드 수를 비교. 루프 본문의 상수식이 단일 `Literal`로 접혀 **실행 중 연산 0회**임을 단언(`(1 - 2*3*4*5/6 + 7+8+9) % 1000 % 30` → 리터럴 5). 0 나누기 부분식은 폴딩되지 않아 런타임 오류 의미가 보존됨도 단언.

> 규칙을 빠뜨리지 않으려면 스펙을 위→아래로 훑으며 각 항목에 대응 테스트가 있는지 대조한다. 일반화: "이 스펙 문장이 깨지면 어떤 단언이 실패하는가?"에 답이 없으면 테스트가 빠진 것이다.

## 유의사항 (3일차~)

PDF 유의사항: 3일차부터는 **TDD 필수가 아니다**(테스트를 구현과 함께/뒤에 써도 됨). 단 (1) **기존 UnitTest는 유지보수**되어야 하고(약화 금지), (2) **추가 기능마다 UnitTest를 반드시 생성**한다. test-author는 신규 기능별 테스트 파일(`FunctionTest`, `ArrayTest`, `OptimizationTest`, `DebugModeTest`, `FileModeTest`)을 추가한다.

## 회귀·신규 기능

새 토큰/연산자/문을 추가할 때는 먼저 그 기대 동작과 에러 동작을 테스트로 추가한 뒤 엔지니어에게 통지한다. 기존 테스트는 절대 약화시키지 않는다(통과시키려고 단언을 느슨하게 만들지 않는다 — 구현을 고친다).

## 출력

테스트 파일을 `src/test/java/codefab/`에 작성한다. 파일 단위로 관심사를 나눈다: `ScannerTest`, `ParserTest`, `CheckerTest`, `NormalOperationTest`, `ErrorTest`, `SessionTest`, `PromptShellTest`. 각 테스트 메서드 이름은 검증 대상을 서술한다(`danglingElseBindsToNearestIf`).
