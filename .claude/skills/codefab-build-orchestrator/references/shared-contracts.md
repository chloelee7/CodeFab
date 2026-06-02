# 공유 계약 (Shared Contracts)

이 파일은 CodeFab 인터프리터 파이프라인의 모든 유닛이 공유하는 데이터 계약을 정의한다. Assembler·Checker·Executor·Shell·QA 에이전트는 작업 시작 전 이 파일을 반드시 읽고, 계약을 변경해야 하면 먼저 팀에 `SendMessage`로 알린 뒤 합의한다. 계약은 한 곳에서만 정의되고, 그 정의가 경계면 버그를 막는 기준선이다.

## 목차
1. 패키지 레이아웃
2. Token / TokenType 계약 (Scanner ↔ Parser 경계)
3. Expr / Stmt AST 계약 (Parser ↔ Checker ↔ Executor 경계)
4. Diagnostic 계약 (모든 유닛 ↔ Facade 경계)
5. OutputSink 계약 (Executor ↔ Facade 경계)
6. Facade 계약 (Shell ↔ 사용자 경계)
7. 진단 메시지 문자열 (스펙 고정값)

---

## 1. 패키지 레이아웃

```
codefab            facade: CodeFab, CodeFabSession, RunResult, CollectingOutputSink
codefab.core       공유: Token, TokenType, Expr, Stmt, Diagnostic, OutputSink, InterpreterRuntimeError
codefab.assembler  Scanner, Parser, ParseError
codefab.checker    Checker
codefab.executor   Environment, Executor
codefab.shell      PromptShell, Main
```

설계 불변식: **Expr는 Expr·Token만 필드로 가진다. Stmt는 Expr·Stmt·Token을 가질 수 있다. Token은 AST 노드가 아니라 노드의 필드다.** Expr 안에 Stmt를 넣지 않는다.

## 2. Token / TokenType 계약

`Token`은 불변 값 객체: `TokenType type, String lexeme, Object literal, int line`. NUMBER 토큰의 `literal`은 `Double`, STRING 토큰의 `literal`은 따옴표를 벗긴 `String`. 그 외에는 `null`.

`TokenType` (정확히 이 집합):
```
LEFT_PAREN RIGHT_PAREN LEFT_BRACE RIGHT_BRACE COMMA DOT SEMICOLON
PLUS MINUS STAR SLASH
BANG BANG_EQUAL EQUAL EQUAL_EQUAL GREATER GREATER_EQUAL LESS LESS_EQUAL
IDENTIFIER STRING NUMBER
AND OR IF ELSE TRUE FALSE FOR VAR PRINT
EOF
```
토큰 리스트는 항상 `EOF` 토큰으로 끝난다. Parser는 이를 종료 신호로 의존한다.

## 3. Expr / Stmt AST 계약

방문자 패턴(`accept(Visitor<R>)`). Checker와 Executor는 이 인터페이스로만 순회하며, 노드 정의에 의존하지 않는다.

**Expr** (각각 final 클래스, `visitXxx` 방문 메서드):
- `Literal(Object value)`
- `Variable(Token name)`
- `Assign(Token name, Expr value)`
- `Unary(Token operator, Expr right)`
- `Binary(Expr left, Token operator, Expr right)`
- `Logical(Expr left, Token operator, Expr right)`
- `Grouping(Expr expression)`

**Stmt**:
- `ExpressionStmt(Expr expression)`
- `PrintStmt(Expr expression)`
- `VarStmt(Token name, Expr initializer)` — initializer는 nullable
- `BlockStmt(List<Stmt> statements)`
- `IfStmt(Expr condition, Stmt thenBranch, Stmt elseBranch)` — elseBranch nullable
- `ForStmt(Stmt initializer, Expr condition, Expr increment, Stmt body)` — initializer/condition/increment nullable

nullable 필드는 소비자(Checker/Executor)가 반드시 null 체크한다. 이것이 경계면 버그의 주요 지점이다.

## 4. Diagnostic 계약

`Diagnostic(Stage stage, int line, String message)`, `Stage ∈ {SCANNER, PARSER, CHECKER, RUNTIME}`. 각 유닛은 자기 단계의 진단만 생성한다. 진단은 던지지 않고 수집 리스트(`List<Diagnostic>`)에 추가한다(런타임 오류 제외 — 아래 참조). line 번호를 가능한 한 포함한다.

**단계별 책임 경계 (엄수):**
- SCANNER: 알 수 없는 문자, 미종료 문자열
- PARSER: 모든 구문 오류
- CHECKER: 의미상 정적 오류 2종만 (중복 선언, 자기 초기화 읽기). 실행 금지.
- RUNTIME: Executor가 던지는 `InterpreterRuntimeError`를 Facade가 잡아 RUNTIME 진단으로 변환

## 5. OutputSink 계약

`interface OutputSink { void print(String line); }`. Executor는 `System.out`을 직접 쓰지 않고 주입된 `OutputSink`로만 출력한다. 테스트와 REPL이 출력을 가로채는 근거다.

## 6. Facade 계약

- `RunResult`: `boolean success()`, `List<String> output()`, `List<Diagnostic> diagnostics()`.
- `CodeFabSession.run(String): RunResult` — REPL용. global `Environment`는 run 간 유지, Checker는 run마다 새로 생성, output은 run마다 초기화.
- `CodeFab.run(String): RunResult` — 매번 새 세션으로 단발 실행.
- 파이프라인 순서: Assembler(scan+parse) → 진단 있으면 중단 → Checker → 진단 있으면 중단(실행 금지) → Executor(런타임 오류는 진단으로 변환).

## 7. 진단 메시지 문자열 (스펙 고정값 — 변경 금지)

이 부분 문자열들은 테스트가 `contains()`로 검사한다. 정확히 포함되어야 한다.

| 단계 | 메시지 부분 문자열 |
|------|------------------|
| PARSER | `Expect ';' after value.` |
| PARSER | `Expect ')' after expression.` |
| PARSER | `Invalid assignment target.` |
| PARSER | `Expect expression.` |
| CHECKER | `Can't read local variable in initializer.` |
| CHECKER | `Already a variable with this name in this scope.` |
| RUNTIME | `Undefined variable '<name>'.` |
| RUNTIME | `Operands must be two numbers or two strings.` |
| RUNTIME | `Operand must be a number.` (단항 -, +) |
| RUNTIME | `Operands must be numbers.` (이항 산술/비교) |
| RUNTIME | `Division by zero.` |

파서는 메시지 뒤에 `" at '<lexeme>'"` 또는 `" at end"`를 덧붙일 수 있다(부분 문자열 검사는 그대로 통과).
