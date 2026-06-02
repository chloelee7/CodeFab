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
