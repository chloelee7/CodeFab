---
name: codefab-build-orchestrator
description: "CodeFab Java 인터프리터(Assembler→Checker→Executor 파이프라인) 개발 에이전트 팀을 조율하는 오케스트레이터. 인터프리터/스캐너/파서/체커/실행기/REPL 구현·확장, 새 토큰·문법·연산자·문(statement) 추가, AST 노드 추가, 진단 메시지 변경, 인터프리터 버그 수정 요청 시 사용. 함수(선언·호출·return·재귀), 정적 배열(Array·인덱스), 실행 전 최적화(정적 바인딩 distance·상수 폴딩), 공장 제어 쉘(파일 모드·디버그 모드: stepping·breakpoint·watch) 같은 기능 추가도 포함. 후속 작업: 인터프리터 결과 수정, 특정 유닛(스캐너/파서/체커/실행기/셸/디버거)만 다시 구현, 테스트 추가, 기능 보완·업데이트·재실행, 이전 구현 개선 요청 시에도 반드시 이 스킬을 사용. 단순 코드 질문은 직접 응답 가능."
---

# CodeFab Build Orchestrator

CodeFab 트리워킹 인터프리터의 에이전트 팀을 조율하여 TDD 방식으로 인터프리터를 구현·확장한다. 스펙을 실패 테스트로 고정한 뒤, 파이프라인 유닛을 순차 구현하고, 경계면 정합성을 점진적으로 검증한다.

## 실행 모드: 에이전트 팀

3개 유닛 엔지니어 + 셸 + 테스트 작성자 + QA가 공유 계약(Token/AST/Diagnostic)을 두고 협업한다. 계약 변경은 `SendMessage`로 합의하고, 진행은 공유 작업 목록(`TaskCreate`)으로 조율한다. 단일 팀이 빌드 전체 동안 유지된다(파이프라인 + 생성-검증 복합 패턴).

## 시작 전: 공유 계약 로드

작업 시작 전 `references/shared-contracts.md`를 읽는다. 모든 팀원 프롬프트에 이 파일 경로를 포함시켜, 각 유닛이 동일한 Token/AST/Diagnostic 계약 위에서 작업하도록 한다. 이것이 경계면 버그를 막는 기준선이다.

## 에이전트 구성

| 팀원 | 타입 | 역할 | 스킬 | 출력 |
|------|------|------|------|------|
| test-author | general-purpose (opus) | 기능별 UnitTest(+Test Double) | interpreter-tdd | `src/test/java/codefab/*Test.java` |
| assembler-engineer | general-purpose (opus) | Scanner·Parser·AST(함수/배열/Call/Index) | assembler-construction | `core/Token*,Expr,Stmt`, `assembler/*` |
| checker-engineer | general-purpose (opus) | 정적 분석 + 리졸버(distance) + 옵티마이저(폴딩) | checker-analysis | `checker/Checker.java`, `checker/CheckResult.java` |
| executor-engineer | general-purpose (opus) | Environment·평가·함수·배열·distance·디버그 훅 | executor-evaluation | `executor/*` |
| shell-integrator | general-purpose (opus) | facade·REPL·파일/디버그 모드 | shell-repl-construction | `codefab/*`, `shell/*` (Debugger) |
| qa-verifier | general-purpose (opus) | 경계면 정합성·빌드 | interpreter-qa | `_workspace/qa_report.md` |

> 모든 Agent/TeamCreate 호출에 `model: "opus"`를 명시한다.

## 워크플로우

### Phase 0: 컨텍스트 확인 (후속 작업 지원)

기존 산출물을 확인하여 실행 모드를 결정한다:

1. `_workspace/` 디렉토리와 `src/main/java/codefab/` 구현 존재 여부 확인
2. 실행 모드 결정:
   - **구현 없음** → 초기 구현. Phase 1로 진행
   - **구현 존재 + 부분 수정 요청** (예: "파서에 % 연산자만 추가") → 부분 재실행. 영향받는 유닛 엔지니어 + test-author + qa-verifier만 활성화하고, 변경되는 계약을 먼저 식별
   - **구현 존재 + 광범위한 새 요청** → 새 실행. 기존 `_workspace/`를 `_workspace_{YYYYMMDD_HHMMSS}/`로 이동 후 Phase 1
3. 부분 재실행 시: 변경이 공유 계약(Token/AST/Diagnostic)에 닿는지 먼저 판정한다. 닿으면 `references/shared-contracts.md`를 먼저 갱신하고 영향받는 모든 유닛에 통지한다.

### Phase 0.5: 대규모 다기능 요청은 기능별로 단계화

여러 기능군을 한 번에 추가하라는 요청(예: 함수+배열+최적화+디버그)은 **기능별 순차 단계**로 쪼갠다. 각 단계는 그 자체로 Phase 1~5를 한 사이클 돌고, **단계 끝마다 `./gradlew test` 전체 통과를 확인**한 뒤 다음 단계로 넘어간다. 한 번에 모든 계약을 흔들면 경계면 회귀를 디버깅하기 어렵다.

권장 순서(의존도·위험도 기준):
1. **함수** — Func/return 토큰, FunctionStmt/ReturnStmt/Call, 클로저, 함수 정적/런타임 오류.
2. **정적 배열** — `[`·`]` 토큰, Index/IndexSet, 네이티브 Array, 배열 런타임 오류.
3. **실행 전 최적화** — Checker를 리졸버+옵티마이저로(distance 맵 + 상수 폴딩), Environment getAt/assignAt, Test Double 검증. (함수/배열이 자리 잡은 AST 위에서 해석/폴딩해야 안전.)
4. **공장 제어 쉘** — run/debug 서브커맨드, 파일 모드, 디버거(ExecutionObserver 훅 + Command 명령).

각 단계는 계약(`shared-contracts.md`)의 해당 절을 단일 진실로 삼는다. 단계 진입 시 그 절을 팀에 다시 짚어준다.

### Phase 1: 준비

1. 사용자 입력 분석 — 어떤 토큰/문법/노드/의미/런타임 규칙이 추가·변경되는지 식별
2. 작업 디렉토리에 `_workspace/` 생성 (새 실행 시 기존 것을 타임스탬프 디렉토리로 이동한 직후)
3. 변경되는 스펙 조각과 영향받는 계약을 `_workspace/00_spec.md`에 정리
4. 빌드 시스템 확인: 기존 Gradle 구조를 존중한다. 빈 프로젝트면 Gradle Java(JUnit 5, 패키지 `codefab`)를 생성한다.

### Phase 2: 팀 구성

1. 팀 생성 (`TeamCreate`, team_name `codefab-team`). 각 팀원 프롬프트에: 역할 한 줄 + 담당 스킬 호출 지시 + `references/shared-contracts.md` 경로 + `_workspace/00_spec.md` 경로를 포함.
2. 작업 등록 (`TaskCreate`) — 파이프라인 의존성을 `depends_on`으로 명시:

```
TaskCreate(tasks: [
  { title: "스펙을 실패 테스트로 고정",     assignee: "test-author" },
  { title: "Token/TokenType/AST 정의",      assignee: "assembler-engineer", depends_on: ["스펙을 실패 테스트로 고정"] },
  { title: "Scanner 구현",                  assignee: "assembler-engineer", depends_on: ["Token/TokenType/AST 정의"] },
  { title: "Parser 구현 + 에러 복구",        assignee: "assembler-engineer", depends_on: ["Scanner 구현"] },
  { title: "Assembler 경계면 QA",            assignee: "qa-verifier",        depends_on: ["Parser 구현 + 에러 복구"] },
  { title: "Checker 구현",                  assignee: "checker-engineer",   depends_on: ["Token/TokenType/AST 정의"] },
  { title: "Checker 경계면 QA",              assignee: "qa-verifier",        depends_on: ["Checker 구현"] },
  { title: "Environment + Executor 구현",    assignee: "executor-engineer",  depends_on: ["Token/TokenType/AST 정의"] },
  { title: "Executor 경계면 QA",             assignee: "qa-verifier",        depends_on: ["Environment + Executor 구현"] },
  { title: "Facade + PromptShell + Main",   assignee: "shell-integrator",   depends_on: ["Parser 구현 + 에러 복구","Checker 구현","Environment + Executor 구현"] },
  { title: "전체 파이프라인 QA + gradle test", assignee: "qa-verifier",       depends_on: ["Facade + PromptShell + Main"] },
])
```

> AST 정의가 끝나면 checker/executor/parser가 병렬로 진행될 수 있다(파이프라인 내 병렬 구간). test-author는 각 유닛이 시작되기 전에 해당 유닛의 테스트를 먼저 제공한다.

### Phase 3: 구현 (팀원 자체 조율)

팀원들은 공유 작업 목록에서 작업을 claim하고 수행한다. 리더는 모니터링하며 막힌 팀원을 돕는다.

**팀원 간 통신 규칙:**
- assembler-engineer가 Token/AST 정의를 완료하면 `SendMessage({to:"all"})`로 "계약 확정"을 1회 브로드캐스트(이후 변경은 개별 통지).
- 계약 변경이 필요한 팀원은 변경안을 리더와 assembler-engineer에게 보내 합의 후 `references/shared-contracts.md`를 갱신한다. 합의 없는 독단적 계약 변경 금지.
- qa-verifier는 경계면 이슈 발견 시 **양쪽 유닛 모두**에게 파일:라인 + 수정 방법을 보낸다.
- test-author는 새 테스트 추가 시 해당 유닛 엔지니어에게 통지한다.

**점진적 QA (중요):** qa-verifier는 전체 완성 후 1회가 아니라, **각 유닛 완성 직후** 해당 유닛의 경계면을 검증한다(Assembler→Checker→Executor 순). 누적 전파를 막는다.

### Phase 4: 통합 검증

1. 모든 작업 완료 확인 (`TaskGet`)
2. qa-verifier가 `./gradlew test`로 전체 테스트 통과를 확인하고 `_workspace/qa_report.md`에 통과/실패/미검증을 기록
3. 실패가 있으면 해당 유닛 엔지니어에게 1회 재작업 요청(최대 재시도 2회). 재실패 시 리포트에 명시하고 진행
4. Definition of Done 확인(기본): 정상 테스트 통과 / 오류 테스트 진단 발생 / REPL이 `var a=5; var b=10; print a+b;`에 15 출력 / 멀티라인 블록 동작 / README 존재
5. 기능별 DoD(해당 단계에서만):
   - **함수**: `Func add(a,b){return a+b;} print add(3,7);`→10, `fact(5)`→120, `return;`→nil, 함수 외부 return·파라미터 중복(CHECKER), 함수 아닌 대상 호출·인자 개수(RUNTIME).
   - **배열**: `var a=Array(3); a[0]=10; print a[0];`→10, `a[i-1]` 식 인덱스, 범위/비숫자 인덱스/비배열/비숫자 크기 런타임 오류.
   - **최적화**: distance 맵으로 O(1) 접근(Test Double로 체인 미상승 단언), 상수식 폴딩으로 실행 중 연산 0회(Test Double로 단언), 0 나누기 부분식은 폴딩 안 됨.
   - **공장 제어 쉘**: `run <file>`(파일 부재 메시지·런타임 오류 줄번호), `debug <file>`(step/next/break/continue/breakpoints/remove + watch/unwatch/watches/inspect) 통합 테스트 통과.
   - **공통**: 기존 UnitTest 전부 유지(약화 금지), 신규 기능마다 UnitTest 추가, README에 사용법·특이사항 문서화.

### Phase 5: 정리

1. 팀원 종료 요청 후 `TeamDelete`
2. `_workspace/` 보존 (감사 추적용)
3. 의미 있는 마일스톤마다 커밋(git 사용 가능 시): 테스트 스캐폴딩 → 스캐너 → 파서·AST → 체커 → 실행기 → 셸·문서 → 리팩터
4. 사용자에게 결과 요약 + 피드백 요청 (Phase 7 진화 트리거)

## 데이터 흐름

```
[리더] → TeamCreate + TaskCreate
   test-author ──(테스트)──▶ 각 유닛 엔지니어
   assembler-engineer ──(Token/AST 계약)──▶ checker / executor / shell
        │                                          │
   Scanner→Parser AST ───────────────────────────▶ Checker, Executor
        │              │              │             │
        └── qa-verifier가 각 경계면을 양쪽 동시 읽기로 교차 검증 ──┘
                                  ▼
                   gradle test 통과 → 최종 산출물
```

전달 전략: 코드는 직접 소스 트리에 작성(파일 기반), 조율은 TaskCreate(태스크 기반), 계약/이슈는 SendMessage(메시지 기반). QA 리포트만 `_workspace/`에 둔다.

## 에러 핸들링

| 상황 | 전략 |
|------|------|
| 유닛 구현이 테스트 실패 | qa-verifier가 실패 테스트명+메시지를 해당 엔지니어에 전달, 1회 재시도(최대 2회) |
| 계약 충돌 (두 유닛이 AST를 다르게 해석) | 리더 중재 → shared-contracts.md를 단일 진실로 갱신 → 양쪽 재정렬 |
| 팀원 1명 중지 | 리더가 유휴 알림 수신 → 상태 확인 → 재시작 또는 작업 재할당 |
| gradle/JDK 환경 문제 | JDK 17 부재 시 21로 컴파일하되 `options.release=17` 사용. 네트워크 불가 시 `--offline` |
| 재시도 2회 후에도 실패 | qa_report.md에 미해결 명시 후 진행, 사용자에게 보고 |

## 테스트 시나리오

### 정상 흐름
1. 사용자가 "CodeFab 인터프리터 구현" 요청
2. Phase 1에서 토큰/문법/의미/런타임 규칙을 `_workspace/00_spec.md`로 정리
3. Phase 2에서 6명 팀 + 11개 작업 등록
4. Phase 3에서 test-author가 실패 테스트 작성 → assembler가 계약 확정 브로드캐스트 → checker/executor/shell 병렬 구현 → qa가 각 유닛 직후 경계면 검증
5. Phase 4에서 `./gradlew test` 전체 통과, DoD 충족
6. Phase 5에서 마일스톤 커밋 + 팀 정리
7. 예상 결과: 전체 테스트 통과하는 인터프리터 + README

### 에러 흐름
1. Phase 3에서 executor-engineer가 `+` 연산자에서 String+Number를 허용해 테스트 실패
2. qa-verifier가 "Operands must be two numbers or two strings." 미발생을 감지, executor-engineer에 파일:라인 통지
3. executor-engineer가 타입 가드 수정 → qa 재검증 통과
4. 만약 2회 재시도 후에도 실패하면 qa_report.md에 명시하고 나머지 유닛은 계속 진행
