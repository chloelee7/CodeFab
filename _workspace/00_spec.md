# 스펙: while 문 추가 (부분 재실행)

## 요청
CodeFab에 `while` 반복문을 추가한다.

## 문법 변경
```
statement  -> printStmt | ifStmt | forStmt | whileStmt | block | exprStmt ;
whileStmt  -> "while" "(" expression ")" statement ;
```

## 계약 변경 (shared-contracts.md에 반영됨)
- TokenType: `WHILE` 추가
- Scanner 키워드 맵: `"while" -> WHILE`
- Stmt AST: `WhileStmt(Expr condition, Stmt body)` 추가 + 방문자 메서드 `visitWhileStmt`
- 이 변경은 `Stmt.Visitor` 인터페이스에 메서드를 추가하므로, Checker·Executor는 반드시 `visitWhileStmt`를 구현해야 컴파일된다.

## 의미
- 조건이 참인 동안 본문을 반복 실행한다.
- 조건은 매 반복 전에 평가한다. 거짓이면 즉시 종료.
- while은 자체 루프 변수를 선언하지 않으므로 for와 달리 전용 Environment가 필요 없다(본문이 블록이면 블록이 자기 스코프를 연다).

## 진단
- `Expect '(' after 'while'.`
- `Expect ')' after condition.`
- 본문 파싱은 statement()에 위임.

## 영향 유닛 / 작업
| 유닛 | 작업 |
|------|------|
| test-author | WhileTest 실패 테스트 선작성 (정상 반복, 조건 거짓 시 미실행, 중첩) |
| assembler-engineer | WHILE 토큰 + 키워드, WhileStmt 노드 + visitWhileStmt, whileStatement() 파싱 |
| checker-engineer | visitWhileStmt: resolve(condition); resolve(body) |
| executor-engineer | visitWhileStmt: while(isTruthy(evaluate(condition))) execute(body) |
| qa-verifier | 경계면(AST 계약 ↔ 두 방문자) 교차 검증 + ./gradlew test |
| shell-integrator | (제외 — facade/REPL 변경 없음) |

## 파이프라인 순서
test-author → assembler-engineer(계약 확정) → {checker-engineer ∥ executor-engineer} → qa-verifier
