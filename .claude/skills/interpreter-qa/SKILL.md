---
name: interpreter-qa
description: "인터프리터/컴파일러 파이프라인의 통합 정합성을 검증하는 QA. 스테이지 간 경계면(Scanner↔Parser 토큰 계약, Parser↔Checker↔Executor의 AST 계약, 진단 단계 계약, 출력 포맷 계약)을 양쪽 동시 읽기로 교차 검증하고 gradle test로 회귀를 확인. 인터프리터 구현 검증, 경계면 버그 탐색, 빌드·테스트 통과 확인, 스펙 준수 검사에 사용. CodeFab 인터프리터의 qa-verifier가 사용."
---

# Interpreter QA — 경계면 정합성 검증

각 유닛이 따로따로 맞아도, 유닛이 만나는 경계면에서 계약이 어긋나면 런타임에 실패한다. QA의 핵심은 "이 함수가 존재하는가?"가 아니라 **"생산자가 내보내는 모양과 소비자가 기대하는 모양이 일치하는가?"**를 확인하는 것이다. 그래서 항상 **양쪽 코드를 동시에 열어** 비교한다.

## 검증 우선순위

1. **경계면 정합성** (가장 높음) — 스테이지 간 계약 불일치가 런타임 실패의 주원인
2. **스펙 준수** — 진단 메시지 문자열, 출력 포맷, 의미 규칙
3. **회귀** — `./gradlew test` 전체 통과
4. **코드 품질** — 단계 책임 침범(파서에 실행 로직, 체커에 실행 등)

## 양쪽 동시 읽기 — 인터프리터 경계면

| 경계면 | 왼쪽 (생산자) | 오른쪽 (소비자) | 흔한 불일치 |
|--------|-------------|----------------|-----------|
| 토큰 계약 | Scanner의 `addToken`/literal 타입 | Parser의 `match`/`previous().literal` 사용 | NUMBER literal이 Double인데 파서가 다른 타입 기대; EOF 토큰 누락 |
| AST 계약 | Parser가 생성하는 노드 필드 | Checker/Executor의 `visitXxx`가 읽는 필드 | nullable 필드(elseBranch, for의 절들, VarStmt.initializer) null 체크 누락 |
| 진단 단계 | 각 유닛이 다는 `Diagnostic.Stage` | 테스트가 단언하는 stage + 메시지 | 체커 오류가 런타임에서 잡힘; 메시지 문자열 오타 |
| 출력 계약 | Executor의 `stringify`/`OutputSink` | RunResult.output()과 테스트 단언 | `5.0`이 `5.0`으로 출력(`.0` 미제거); System.out 직접 사용 |
| 파이프라인 단락 | Facade의 단계별 중단 로직 | "체커 실패 시 실행 안 됨" 테스트 | 체커 진단이 있는데 Executor가 실행되어 출력 발생 |
| 세션 상태 | CodeFabSession의 전역 Environment 유지 | "변수 입력 간 유지" 테스트 | run마다 Environment 재생성으로 변수 소실; output 미초기화로 누적 |
| **distance 맵** | Checker `CheckResult.locals()` (Map<Expr,Integer>) | Executor의 `locals.get(expr)` → getAt/assign 분기 | 노드 동일성 키 불일치(폴딩으로 노드가 교체됐는데 옛 노드로 조회); 전역인데 맵에 들어가 잘못된 distance |
| **폴딩 AST** | Checker `CheckResult.program()` (폴딩된 트리) | Executor가 실행하는 트리 | Facade가 폴딩 전 원본을 실행; 0 나누기 부분식이 잘못 폴딩되어 `Division by zero.` 소실 |
| **callable/배열 값** | Executor의 CodeFabFunction/CodeFabArray/Return | Call/Index/IndexSet 평가, arity 검사 | 인자 개수/타입 검사 누락; Return 예외가 함수 밖으로 누출; 배열 경계 미검사 |
| **디버그 훅** | Executor `beforeStmt(stmt,line,env,depth)` | Shell Debugger의 step/next/breakpoint | depth 부정확으로 next가 블록에 진입; observer null일 때 NPE; watch가 옛 Environment 조회 |
| **CLI 모드** | Main의 run/debug 디스패치 | 파일/디버그 모드 기대 출력 | 파일 부재 메시지 누락; 런타임 오류에 줄번호 빠짐 |

## 검증 절차

1. **계약 대조**: `references/shared-contracts.md`를 기준으로 각 유닛의 실제 시그니처가 계약과 일치하는지 본다. 불일치는 계약 위반으로 보고.
2. **AST nullable 추적**: Parser가 null을 채울 수 있는 필드(elseBranch, ForStmt의 initializer/condition/increment, VarStmt.initializer)마다, Checker와 Executor의 대응 `visit`에 null 가드가 있는지 **양쪽을 같이 열어** 확인한다.
3. **진단 문자열·단계 대조**: 스펙 고정 문자열(계약 7절)을 Grep으로 코드에서 찾아, 정확히 일치하고 올바른 Stage로 보고되는지 확인한다.
4. **출력 포맷 스폿체크**: `5`, `5.0`, `3.14`, `true`, 문자열, nil의 `stringify` 결과를 추적한다.
5. **빌드·테스트**: `./gradlew test`(필요 시 `--offline`, JDK 17 부재 시 `release=17`로 21 사용)를 돌려 전체 통과를 확인하고, 실패 테스트명과 메시지를 기록한다.

## 점진적 QA

전체 완성 후 1회가 아니라 **각 유닛 완성 직후** 그 유닛의 경계면을 검증한다(Assembler→Checker→Executor 순). 초기 경계면 불일치가 후속 유닛으로 전파되는 비용을 막는다.

## 발견 보고 프로토콜

- 경계면 이슈는 **양쪽 유닛 엔지니어 모두**에게 `파일:라인 + 기대 vs 실제 + 수정 방법`으로 통지한다(SendMessage).
- 단순 회귀(테스트 실패)는 해당 유닛 엔지니어에게 실패 테스트명·단언·실제 메시지를 전달한다.
- 리더에게: 통과/실패/미검증을 구분한 리포트를 `_workspace/qa_report.md`로 남긴다. "확인 못 한 것"을 "통과"로 표기하지 않는다.

## 약한 체크 vs 강한 체크

| 약함 | 강함 |
|------|------|
| 파서가 IfStmt를 만드는가? | 파서가 만든 elseBranch=null을 Executor가 null 가드로 처리하는가? |
| 런타임 오류가 던져지는가? | 정확한 메시지가 Stage.RUNTIME으로, 올바른 줄 번호와 함께 나오는가? |
| 변수 스코프가 있는가? | 블록 실행 중 런타임 오류가 나도 이전 Environment가 복원되는가? |
| 테스트가 통과하는가? | 단언이 스펙의 모든 규칙을 덮는가, 빠진 규칙은 없는가? |
