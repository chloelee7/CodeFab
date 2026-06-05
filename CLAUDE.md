# CodeFab

Java 17 트리워킹 인터프리터. 소스를 Assembler(Scanner→Parser) → Checker → Executor 3단계 파이프라인으로 처리한다. 빌드: Gradle(JUnit 5). 테스트: `./gradlew test`.

## 하네스: CodeFab 인터프리터 개발

**목표:** TDD 방식으로 CodeFab 인터프리터를 구현·확장한다. 스펙을 실패 테스트로 고정하고, 파이프라인 유닛(스캐너·파서·체커·실행기·셸)을 전문 에이전트 팀이 구현하며, 경계면 정합성을 점진적으로 검증한다.

**트리거:** 인터프리터/스캐너/파서/체커/실행기/REPL 구현·확장, 새 토큰·문법·연산자·문·AST 노드 추가, 진단 메시지 변경, 인터프리터 버그 수정, 특정 유닛만 재구현, 테스트 추가 등의 작업 요청 시 `codefab-build-orchestrator` 스킬을 사용하라. 단순 코드 질문은 직접 응답 가능.

**공유 계약:** 모든 유닛은 `.claude/skills/codefab-build-orchestrator/references/shared-contracts.md`의 Token/AST/Diagnostic 계약을 단일 진실로 삼는다.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-06-02 | 초기 구성 (에이전트 6 + 스킬 7) | 전체 | CodeFab 인터프리터 개발 하네스 구축 |
| 2026-06-02 | while 문 추가 (부분 재실행) | TokenType·Scanner·Stmt·Parser·Checker·Executor + WhileTest | 하네스 라이브 구동: 새 문법 추가 데모 |
| 2026-06-05 | 3일차 요구사항 반영 위한 하네스 진화 | shared-contracts(§3,4,6,7 확장 + §8~10 신설)·checker-analysis(리졸버+옵티마이저)·executor-evaluation(함수/배열/distance/디버그훅)·shell-repl-construction(파일/디버그 모드)·assembler-construction(함수/배열 문법)·interpreter-tdd(Test Double)·interpreter-qa(신규 경계면)·checker/executor/shell 에이전트·orchestrator(단계화·DoD) | 함수·정적 배열·실행 전 최적화·공장 제어 쉘 4개 기능군 추가 준비 |
| 2026-06-05 | 4개 기능군 단계별 구현(함수→배열→최적화→공장제어쉘) | core/assembler/checker/executor/shell + FunctionTest·ArrayTest·OptimizationTest·FileModeTest·DebugModeTest | 3일차 PDF 요구사항 구현 완료. 101개 테스트 통과, 경계면 결함 0건. GoF(Visitor/Strategy/Command/Interpreter) 적용 |
| 2026-06-05 | REPL 교차-run 함수 호출 버그 수정 + 하네스 반영 | CodeFabSession(distance 맵 누적)·SessionTest(회귀 2)·interpreter-qa(상태 수명 경계면)·interpreter-tdd(교차-run 테스트 항목) | 저장된 함수 본문의 distance가 run마다 맵 교체로 소실되던 버그. 같은 유형 재발 방지 위해 "상태 수명×최적화" 경계면을 QA 스킬에 추가 |
