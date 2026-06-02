# QA 리포트: while 문 통합 정합성 검증

## 경계면 교차 검증 (양쪽 동시 읽기) — 전부 통과
1. **AST 계약 ↔ 두 소비자**: `Stmt.WhileStmt(condition, body)` 필드명을 Checker.visitWhileStmt / Executor.visitWhileStmt가 동일하게 사용. non-nullable 일관(Parser가 expression()/statement()로 항상 채움) → Executor가 condition null 가드를 두지 않은 것은 ForStmt와 달리 계약상 올바름.
2. **토큰 계약**: TokenType.WHILE ↔ Scanner `"while"` 키워드 ↔ Parser match(WHILE) 일관.
3. **진단 문자열·단계**: `Expect '(' after 'while'.` / `Expect ')' after condition.`가 PARSER 단계로 보고됨.

## 발견 및 처리
- **경계면 차단 결함(해소됨)**: `WhileTest.java`가 `import codefab.core.Diagnostic;` 누락 → compileTestJava 실패. 담당 test-author에게 통지 → 1줄 import 추가로 해소.

## 빌드·테스트
- 환경: JDK 21 + `release=17`.
- 결과: **BUILD SUCCESSFUL, 63 PASSED, 실패 0** (WhileTest 6개 + 기존 회귀 57개).

## 결론
**통과.** while 기능의 product 통합 정합성 전 항목 통과, 회귀 0. 라이브 실행(`0 1 2`, `10`) 확인.
