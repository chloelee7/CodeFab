---
name: shell-repl-construction
description: "인터프리터의 facade와 대화형 셸(REPL)·CLI 진입점 구현. 파이프라인(Assembler→Checker→Executor)을 엮는 facade(run→RunResult), 영속 상태 세션(REPL용), 멀티라인 입력 누적 셸, 파일 실행/REPL/--help를 지원하는 Main 작업에 사용. CodeFab 인터프리터의 shell-integrator가 사용. facade/REPL/CLI/세션/Main/PromptShell 작업이면 이 스킬을 사용할 것."
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

## Main — CLI

- 인자 없음 → `PromptShell` 시작(stdin 리더 주입).
- 파일 경로 1개 → 파일을 읽어 `CodeFab().run`, 출력은 stdout, 진단은 stderr, 실패 시 비정상 종료 코드.
- `--help`/`-h` → 사용법 출력.

테스트 가능성을 위해 `PromptShell`은 `BufferedReader`와 `PrintStream`을 주입받게 설계한다(파이프로 구동되는 통합 테스트 가능). 완성 판정 메서드는 단위 테스트할 수 있도록 공개한다.

## 출력

`codefab` 패키지에 CodeFab/CodeFabSession/RunResult/CollectingOutputSink, `codefab.shell`에 PromptShell/Main. README에 문법·예시·아키텍처·테스트 실행법을 문서화한다.
