---
name: shell-repl-construction
description: "인터프리터의 facade와 공장 제어 쉘(REPL·파일·디버그 모드)·CLI 진입점 구현. 파이프라인(Assembler→Checker→Executor)을 엮는 facade(run→RunResult), 영속 상태 세션(REPL용), 멀티라인 입력 누적 셸, run/debug 서브커맨드 Main, 파일 모드(줄번호 런타임 오류), 디버그 모드(step/next/break/continue/watch/inspect, Stmt 단위 stepping과 breakpoint)를 지원. CodeFab 인터프리터의 shell-integrator가 사용. facade/REPL/CLI/세션/Main/PromptShell/파일모드/디버그모드/stepping/breakpoint/watch/공장제어쉘 작업이면 이 스킬을 사용할 것."
---

# Shell & REPL Construction — Facade · REPL · CLI

세 유닛을 하나의 파이프라인으로 엮고, 그것을 스크립트 실행과 대화형 REPL로 노출한다. 핵심 가치는 출력·진단을 구조화된 결과로 반환하여 테스트가 `System.out`에 의존하지 않게 하는 것이다.

## 작업 시작 전

`references/shared-contracts.md`에서 `RunResult`, `OutputSink`, `Diagnostic`, 세션 동작 계약을 확인한다.

## Facade — 파이프라인 결합

`CodeFabSession.run(String): RunResult`가 파이프라인을 순서대로 실행한다:

1. **Assembler**: Scanner→Parser. 진단이 하나라도 있으면 **즉시 실패 반환**(체커·실행기 진행 금지).
2. **Checker**: 새 Checker로 정적 분석. 진단이 있으면 **실패 반환(실행 금지)**.
3. **Executor**: 실행. `InterpreterRuntimeError`를 잡아 Stage.RUNTIME 진단으로 변환.

`RunResult`는 `success()`(진단 없음), `output()`(List<String>), `diagnostics()`를 노출한다. 단계별 단락(short-circuit)이 핵심 — 앞 단계 오류가 있으면 뒤 단계를 돌리지 않는다.

## 출력 주입

Executor에 `CollectingOutputSink`(List에 라인 누적)를 주입한다. `System.out`을 코어에 두지 않으므로 테스트가 출력을 그대로 검증한다.

## 세션 vs 단발 실행

- **CodeFabSession** (REPL용): 전역 `Environment`를 run 간 **유지**한다. 그래서 이전 입력의 변수를 다음 입력이 읽는다. 단, Checker는 run마다 **새로** 만든다(입력 간 전역 재선언이 중복으로 잡히지 않게). output은 run 시작 시 초기화하여 각 run이 자기 출력만 반환한다. 런타임 오류가 나도 세션은 죽지 않는다.
- **CodeFab** (단발): 매 `run`마다 새 `CodeFabSession`을 만들어 상태를 공유하지 않는다.

## PromptShell — 멀티라인 누적

줄 단위로 읽되, 입력이 "완성"될 때까지 버퍼에 누적한 뒤 한 번에 실행한다. 완성 판정 휴리스틱:

- 괄호 `()`와 중괄호 `{}`의 균형이 맞아야 한다(열린 채면 계속 입력).
- 문자열·주석 내부의 괄호는 세지 않는다(스캔하며 in-string/in-comment 플래그 관리).
- 균형이 맞고, 내용이 `;` 또는 `}`로 끝나면 완성으로 본다.

```
괄호/중괄호 미균형 또는 미종료 문자열 → 계속 누적
균형 + (";" 또는 "}"로 끝남)            → 실행
```

완성되면 단일 세션의 `run`에 넘기고, 출력과 진단을 출력한다. 변수는 세션이 유지하므로 입력 간 보존된다. 명령: `:exit`/`:quit`로 종료, `:env`는 선택.

## Main — 공장 제어 쉘 (모드 디스패치, Strategy 패턴)

`Main`은 첫 인자(서브커맨드)로 실행 모드를 고른다(계약 §6):

- 인자 없음 → **프롬프트 모드(REPL)**: 기존 `PromptShell`. 전역 저장소 세션 유지, `exit`/`quit` 종료.
- `run <파일경로>` → **파일 모드**.
- `debug <파일경로>` → **디버그 모드**.
- `--help`/`-h` → 사용법.
- 하위호환: 단일 파일 인자(`codefab <file>`)는 `run <file>`과 동일 처리(기존 테스트 보존).

모드 선택을 if-else 더미가 아니라 `Mode` 전략(예: `RunMode`, `DebugMode`, `ReplMode` 각각 `execute(args)`)으로 분리하면 GoF Strategy 추가 점수. 각 모드는 `BufferedReader`/`PrintStream`을 주입받아 테스트 가능하게 한다.

### 파일 모드

`.txt` 소스를 읽어 단발 실행한다.

- 파일 부재 → 명확한 오류(`Could not read file '<path>'`)와 비정상 종료.
- 런타임 오류 → **줄 번호를 포함**해 출력 후 즉시 종료(`RunResult.diagnostics()`의 RUNTIME 진단에 line이 들어 있으므로 `diagnostic.render()`가 줄번호를 포함하도록).
- 정상 출력은 stdout, 진단은 stderr.

### 디버그 모드 (Debugger — Stmt 단위 stepping + breakpoint + watch)

소스를 Stmt 단위로 멈추며 점검한다(계약 §10). 핵심은 **Executor의 `ExecutionObserver` 훅**에 디버거를 꽂아, Stmt 실행 직전 통지를 받아 정지/명령 루프를 도는 것이다.

**Executor 협업:** Executor는 각 Stmt 실행 직전 `observer.beforeStmt(stmt, line, env, depth)`를 호출한다(executor-engineer 담당). 디버거는 이 콜백에서 정지 여부를 판단하고, 정지 시 `>` 프롬프트로 명령을 받는다.

**명령 (Command 패턴 권장 — 명령마다 객체/핸들러):**
- Stepping: `step`(다음 Stmt 정지, 블록 진입 O), `next`(블록 내부 진입 X — 현재 depth 이하로 돌아올 때까지 통지 무시), `break <줄>`, `breakpoints`, `remove <줄>`, `continue`(다음 breakpoint까지).
- Watch: `watch <변수>`/`unwatch <변수>`/`watches`(인접 스코프 값) / `inspect`(현재 스코프 전체 변수+타입).
- 정지 시점마다 감시 변수 자동 출력(`[WATCH] <name> = <value>`).

**출력 라벨:** `[DEBUG]`(로딩·정지), `[WATCH]`(감시값), `inspect`는 `[로컬]`/`[전역] <name> = <value> (<Type>)`. PDF 예시 포맷을 따른다. watch/inspect는 Environment의 `bindings()`/`enclosing()`으로 **변수 저장소를 직접 조회**한다.

테스트 가능성을 위해 디버거도 입력 리더·출력 스트림을 주입받아, 스크립트된 디버그 세션(`step\nbreak 7\ncontinue\n`)으로 통합 테스트한다.

## 출력

`codefab` 패키지에 CodeFab/CodeFabSession/RunResult/CollectingOutputSink, `codefab.shell`에 PromptShell/Main/Debugger(+ 모드 전략, 디버그 명령). README에 문법·예시·아키텍처·세 가지 모드 사용법·테스트 실행법을 문서화한다.
