# 공유 계약 (Shared Contracts)

이 파일은 CodeFab 인터프리터 파이프라인의 모든 유닛이 공유하는 데이터 계약을 정의한다. Assembler·Checker·Executor·Shell·QA 에이전트는 작업 시작 전 이 파일을 반드시 읽고, 계약을 변경해야 하면 먼저 팀에 `SendMessage`로 알린 뒤 합의한다. 계약은 한 곳에서만 정의되고, 그 정의가 경계면 버그를 막는 기준선이다.

## 목차
1. 패키지 레이아웃
2. Token / TokenType 계약 (Scanner ↔ Parser 경계)
3. Expr / Stmt AST 계약 (Parser ↔ Checker ↔ Executor 경계)
4. Diagnostic 계약 (모든 유닛 ↔ Facade 경계)
5. OutputSink 계약 (Executor ↔ Facade 경계)
6. Facade / CLI 계약 (Shell ↔ 사용자 경계)
7. 진단 메시지 문자열 (스펙 고정값)
8. 함수 · 배열 값 모델 (Executor 내부 계약)
9. 정적 바인딩 + 상수 폴딩 계약 (Checker ↔ Executor 최적화 경계)
10. 디버그 모드 계약 (Executor ↔ Shell 관찰 경계)

---

## 1. 패키지 레이아웃

```
codefab            facade: CodeFab, CodeFabSession, RunResult, CollectingOutputSink
codefab.core       공유: Token, TokenType, Expr, Stmt, Diagnostic, OutputSink, InterpreterRuntimeError
codefab.assembler  Scanner, Parser, ParseError
codefab.checker    Checker (정적 분석 + 리졸버 + 옵티마이저), CheckResult
codefab.executor   Environment, Executor, CodeFabCallable, CodeFabFunction, CodeFabArray, Return, ExecutionObserver
codefab.shell      PromptShell, Main, Debugger (+ debug 명령 Command)
```

설계 불변식: **Expr는 Expr·Token·List<Expr>만 필드로 가진다. Stmt는 Expr·Stmt·Token·List<Token>·List<Stmt>를 가질 수 있다. Token은 AST 노드가 아니라 노드의 필드다.** Expr 안에 Stmt를 넣지 않는다(단, 함수 본문은 `FunctionStmt`라는 Stmt가 보유한다 — Expr가 아니다).

## 2. Token / TokenType 계약

`Token`은 불변 값 객체: `TokenType type, String lexeme, Object literal, int line`. NUMBER 토큰의 `literal`은 `Double`, STRING 토큰의 `literal`은 따옴표를 벗긴 `String`. 그 외에는 `null`.

`TokenType` (정확히 이 집합):
```
LEFT_PAREN RIGHT_PAREN LEFT_BRACE RIGHT_BRACE LEFT_BRACKET RIGHT_BRACKET
COMMA DOT SEMICOLON
PLUS MINUS STAR SLASH PERCENT
BANG BANG_EQUAL EQUAL EQUAL_EQUAL GREATER GREATER_EQUAL LESS LESS_EQUAL
IDENTIFIER STRING NUMBER
AND OR IF ELSE TRUE FALSE FOR WHILE VAR PRINT FUNC RETURN
EOF
```

- `LEFT_BRACKET` = `[`, `RIGHT_BRACKET` = `]` (배열 인덱스).
- 키워드 맵: `"while" -> WHILE`, `"Func" -> FUNC`, `"return" -> RETURN` 포함. **`Func`/`return`은 대소문자 그대로의 키워드다** (`Func`는 대문자 F, `return`은 소문자). `Array`는 키워드가 아니라 IDENTIFIER이며, 전역에 바인딩된 네이티브 함수다(§8).
- `PERCENT`(`%`)는 이전 단계에서 이미 존재할 수 있다. 없으면 추가한다(최적화 예시 `% 1000 % 30`이 의존).
- 토큰 리스트는 항상 `EOF` 토큰으로 끝난다. Parser는 이를 종료 신호로 의존한다.

## 3. Expr / Stmt AST 계약

방문자 패턴(`accept(Visitor<R>)`). Checker와 Executor는 이 인터페이스로만 순회하며, 노드 정의에 의존하지 않는다. **노드는 불변(final 필드)** — 최적화 시 폴딩은 노드를 수정하지 않고 새 노드로 교체한다(§9).

**Expr** (각각 final 클래스, `visitXxx` 방문 메서드):
- `Literal(Object value)`
- `Variable(Token name)`
- `Assign(Token name, Expr value)`
- `Unary(Token operator, Expr right)`
- `Binary(Expr left, Token operator, Expr right)`
- `Logical(Expr left, Token operator, Expr right)`
- `Grouping(Expr expression)`
- `Call(Expr callee, Token paren, List<Expr> arguments)` — 함수 호출. `paren`은 닫는 `)` 토큰(오류 줄번호용).
- `Index(Expr target, Token bracket, Expr index)` — 배열 읽기 `arr[i]`. `bracket`은 `[` 토큰.
- `IndexSet(Expr target, Token bracket, Expr index, Expr value)` — 배열 쓰기 `arr[i] = v`. 파서가 `arr[i]` 뒤에 `=`를 만나면 `Index`가 아닌 `IndexSet`을 만든다.

**Stmt**:
- `ExpressionStmt(Expr expression)`
- `PrintStmt(Expr expression)`
- `VarStmt(Token name, Expr initializer)` — initializer는 nullable
- `BlockStmt(List<Stmt> statements)`
- `IfStmt(Expr condition, Stmt thenBranch, Stmt elseBranch)` — elseBranch nullable
- `ForStmt(Stmt initializer, Expr condition, Expr increment, Stmt body)` — initializer/condition/increment nullable
- `WhileStmt(Expr condition, Stmt body)`
- `FunctionStmt(Token name, List<Token> params, List<Stmt> body)` — 함수 선언. params는 매개변수 이름 토큰 리스트(빈 리스트 가능).
- `ReturnStmt(Token keyword, Expr value)` — value는 nullable. `return;`이면 value=null → null 값 반환.

nullable 필드는 소비자(Checker/Executor)가 반드시 null 체크한다. 이것이 경계면 버그의 주요 지점이다. **모든 Stmt는 줄번호를 추적할 수 있어야 한다** — 디버그 모드(§10)와 파일 모드 런타임 오류가 Stmt의 줄번호를 요구한다. Stmt가 대표 토큰을 들고 있지 않으면, 각 Stmt 노드에 `int line`을 추가하거나 보유 토큰/식에서 줄을 유도한다(파서가 Stmt 생성 시 시작 토큰의 line을 기록하는 방식 권장).

## 4. Diagnostic 계약

`Diagnostic(Stage stage, int line, String message)`, `Stage ∈ {SCANNER, PARSER, CHECKER, RUNTIME}`. 각 유닛은 자기 단계의 진단만 생성한다. 진단은 던지지 않고 수집 리스트(`List<Diagnostic>`)에 추가한다(런타임 오류 제외 — 아래 참조). line 번호를 가능한 한 포함한다.

**단계별 책임 경계 (엄수):**
- SCANNER: 알 수 없는 문자, 미종료 문자열
- PARSER: 모든 구문 오류
- CHECKER: 정적으로 확정 가능한 의미 오류만. 다음 4종 + 리졸루션/폴딩(§9). 실행 금지.
  1. 변수 중복 선언 (`Already a variable with this name in this scope.`)
  2. 자기 초기화식에서 변수 읽기 (`Can't read local variable in initializer.`)
  3. **함수 외부에서 return** (`Can't return from top-level code.`) — Checker는 함수 본문 안인지 추적하는 `FunctionType` 상태를 둔다(NONE/FUNCTION).
  4. **파라미터 이름 중복** (`Already a variable with this name in this scope.`) — 함수 본문 스코프에 파라미터를 선언하며 중복 검출(기존 declare 로직 재사용).
- RUNTIME: Executor가 던지는 `InterpreterRuntimeError`를 Facade가 잡아 RUNTIME 진단으로 변환. 함수/배열의 동적 오류는 모두 RUNTIME이다(§7).

> 이전 계약의 "정적 오류 2종만" 제약은 함수 기능 추가로 **4종 + 최적화 패스**로 확장되었다. Checker는 이제 정적 분석기일 뿐 아니라 **리졸버(distance 계산)이자 옵티마이저(상수 폴딩)** 다(§9).

## 5. OutputSink 계약

`interface OutputSink { void print(String line); }`. Executor는 `System.out`을 직접 쓰지 않고 주입된 `OutputSink`로만 출력한다. 테스트와 REPL, 디버거가 출력을 가로채는 근거다.

## 6. Facade / CLI 계약

- `RunResult`: `boolean success()`, `List<String> output()`, `List<Diagnostic> diagnostics()`.
- `CodeFabSession.run(String): RunResult` — REPL용. global `Environment`는 run 간 유지, Checker는 run마다 새로 생성, output은 run마다 초기화.
- `CodeFab.run(String): RunResult` — 매번 새 세션으로 단발 실행.
- **파이프라인 순서**: Assembler(scan+parse) → 진단 있으면 중단 → **Checker(폴딩된 AST + locals 맵 산출)** → 진단 있으면 중단(실행 금지) → Executor(폴딩된 AST와 locals로 실행, 런타임 오류는 진단으로 변환).
  - Checker는 `CheckResult check(List<Stmt> program)`를 노출한다. `CheckResult`는 `List<Stmt> program()`(상수 폴딩된 프로그램)과 `Map<Expr,Integer> locals()`(정적 바인딩 거리)를 보유. 진단은 기존처럼 주입된 리스트에 누적.
  - Executor는 `new Executor(OutputSink sink, Map<Expr,Integer> locals)`로 생성되고 `CheckResult.program()`을 실행한다.

- **CLI 모드 (Strategy 패턴)** — `Main`이 첫 인자로 모드를 분기한다:
  - 인자 없음 → **프롬프트 모드(REPL)**: 기존 `PromptShell`. `exit`/`quit`로 종료, 전역 저장소 세션 유지.
  - `run <파일경로>` → **파일 모드**: `.txt` 소스를 읽어 단발 실행. 파일 부재 시 명확한 오류 메시지(`Could not read file '<path>'`)와 함께 비정상 종료. 런타임 오류 시 **줄 번호를 포함**해 출력 후 즉시 종료.
  - `debug <파일경로>` → **디버그 모드**: §10.
  - `--help`/`-h` → 사용법.
  - 하위호환: 이전 `codefab <file>` 단일 인자 실행은 `run <file>`과 동일하게 처리해도 된다(기존 테스트 보존). 신규 동작은 서브커맨드로 추가.

## 7. 진단 메시지 문자열 (스펙 고정값 — 변경 금지)

이 부분 문자열들은 테스트가 `contains()`로 검사한다. 정확히 포함되어야 한다.

| 단계 | 메시지 부분 문자열 |
|------|------------------|
| PARSER | `Expect ';' after value.` |
| PARSER | `Expect ')' after expression.` |
| PARSER | `Expect ')' after arguments.` |
| PARSER | `Expect ']' after index.` |
| PARSER | `Expect function name.` |
| PARSER | `Expect parameter name.` |
| PARSER | `Invalid assignment target.` |
| PARSER | `Expect expression.` |
| CHECKER | `Can't read local variable in initializer.` |
| CHECKER | `Already a variable with this name in this scope.` |
| CHECKER | `Can't return from top-level code.` |
| RUNTIME | `Undefined variable '<name>'.` |
| RUNTIME | `Operands must be two numbers or two strings.` |
| RUNTIME | `Operand must be a number.` (단항 -, +) |
| RUNTIME | `Operands must be numbers.` (이항 산술/비교) |
| RUNTIME | `Division by zero.` |
| RUNTIME | `Can only call functions.` (함수 아닌 대상 호출) |
| RUNTIME | `Expected <n> arguments but got <m>.` (인자 개수 불일치) |
| RUNTIME | `Can only index arrays.` (배열 아닌 대상에 `[]`) |
| RUNTIME | `Array index must be a number.` (인덱스가 숫자 아님) |
| RUNTIME | `Array index out of bounds.` (범위 초과) |
| RUNTIME | `Array size must be a number.` (`Array(비숫자)`) |

파서는 메시지 뒤에 `" at '<lexeme>'"` 또는 `" at end"`를 덧붙일 수 있다(부분 문자열 검사는 그대로 통과).

## 8. 함수 · 배열 값 모델 (Executor 내부 계약)

런타임 값 타입: `Double`(number), `String`, `Boolean`, `null`, **호출가능(callable)**, **배열**.

- **CodeFabCallable** (Strategy 인터페이스): `int arity(); Object call(Executor executor, List<Object> arguments);`
  - `CodeFabFunction implements CodeFabCallable` — 사용자 정의 함수. `FunctionStmt declaration`과 **클로저 `Environment closure`**(선언 시점 환경)를 보유. `call`은 `new Environment(closure)`를 만들어 파라미터를 바인딩하고 본문을 실행한다. 본문 실행 중 `Return`(아래)을 잡아 그 값을 반환한다. `Return` 없이 끝나면 `null` 반환.
  - 네이티브 `Array` — 전역에 `define("Array", <native callable>)`로 등록. `arity()`는 1, `call`은 인자가 number가 아니면 `Array size must be a number.` 런타임 오류, 맞으면 크기 n의 `CodeFabArray`(전부 null로 초기화) 반환.
- **Return** (제어 흐름용 비검사 예외, 진단 아님): `class Return extends RuntimeException { final Object value; }`. `ReturnStmt` 실행 시 던져지고 `CodeFabFunction.call`이 잡는다. 스택트레이스 불필요(`super(null, null, false, false)`).
- **CodeFabArray**: 고정 크기. 내부 `Object[] elements`. 생성 시 전부 null. 인덱스 접근 시:
  - 인덱스가 number(Double)가 아니면 `Array index must be a number.`
  - 정수가 아니거나 `[0, size)` 범위를 벗어나면 `Array index out of bounds.`
  - 읽기/쓰기 공통 검사. 쓰기는 값을 저장.
- **Call 실행 의미**: callee를 평가 → `CodeFabCallable`이 아니면 `Can only call functions.` → 인자 평가(좌→우) → `arguments.size() != callable.arity()`면 `Expected <arity> arguments but got <n>.` → `callable.call(...)`.
- **Index/IndexSet 실행 의미**: target 평가 → `CodeFabArray`가 아니면 `Can only index arrays.` → 인덱스 평가 후 위 배열 검사 → 읽기/쓰기. IndexSet의 결과값은 대입된 값(식의 값).
- 출력 포맷: 배열·함수 print 포맷은 구현 재량이되 일관성 유지. number는 기존 포맷 규칙 유지(정수면 소수점 제거 등 기존 동작 보존).

## 9. 정적 바인딩 + 상수 폴딩 계약 (Checker ↔ Executor 최적화 경계)

Checker는 DFS 중 다음 두 최적화를 수행하고 결과를 `CheckResult`로 넘긴다.

### 9-1. 정적 바인딩 (변수 거리 해석)
- Checker가 스코프 스택을 운용하며 각 `Variable`/`Assign` 참조에 대해 **거리(distance)** 를 계산한다. distance = 현재 스코프에서 해당 변수가 선언된 스코프까지 거슬러 올라가는 스코프 수(0 = 현재 스코프).
- 결과는 `Map<Expr,Integer> locals` (식별자 식 → 거리). **전역(어느 스코프에서도 못 찾음) 변수는 맵에 넣지 않는다.**
- Executor는 `Variable`/`Assign` 평가 시:
  - locals에 거리가 있으면 `environment.getAt(distance, name)` / `assignAt(distance, name, value)`로 **즉시 접근(O(1))** — 체인을 거슬러 탐색하지 않는다.
  - 거리가 없으면 전역 환경에서 조회(`globals.get(name)`), 없으면 `Undefined variable`.
- Environment 추가 API: `Object getAt(int distance, String name)`, `void assignAt(int distance, Token name, Object value)`, 내부 `Environment ancestor(int distance)`. `ancestor`는 distance만큼 `enclosing`을 따라간다.
- **불변식**: distance가 있는 접근은 절대 `enclosing`을 1회 초과 탐색하지 않는다(Test Double로 검증, §아래).

### 9-2. 상수 폴딩 (수식 합치기)
- Checker가 AST를 후위 순회하며, **런타임 이전에 100% 값이 확정되는 부분식**을 단일 `Literal`로 교체한 새 AST를 만든다.
- 폴딩 가능 조건(전부 만족해야 함): 부분식이 `Literal`이거나, 피연산자가 모두 폴딩되어 상수가 된 `Binary`/`Unary`/`Grouping`/`Logical`이고, **부작용·변수·호출·인덱스가 없음**. `Variable`/`Assign`/`Call`/`Index`/`IndexSet`이 들어가면 그 부분식은 폴딩 불가(상위도 부분만 폴딩).
- number 산술/비교/`%`, boolean 논리, 단항 `-`/`!`만 폴딩 대상. 문자열 `+`도 양변이 상수 문자열이면 폴딩 가능(재량).
- **안전장치**: 폴딩 중 0으로 나누기가 발생하면 **그 부분식은 폴딩하지 않고 원본 유지**(런타임이 `Division by zero.`를 보고하도록). 폴딩이 런타임 오류 의미를 바꾸면 안 된다.
- 노드 불변성 유지: 기존 노드를 수정하지 말고 새 `Literal` 노드로 치환한 새 트리를 반환한다.

### 9-3. Test Double 검증 (QA/test-author 협업)
- **정적 바인딩**: Environment(또는 그 서브클래스/스파이)가 `enclosing`을 따라간 횟수를 카운트. 최적화 후 거리 기반 접근은 스파이 카운트가 distance 이하임을 단언. 즉 "스코프를 거슬러 올라가지 않고 계산된 위치로 즉시 접근"을 검증.
- **상수 폴딩**: 연산 횟수를 세는 스파이(예: Binary 평가 카운터 또는 폴딩 전/후 AST의 Binary 노드 수). 폴딩 후 루프 본문의 상수식 연산 횟수가 N→0이 되었는지 단언.
- 이 검증을 위해 Executor/Environment는 관찰 가능한 훅(observer 또는 protected hook, §10의 ExecutionObserver 재사용 가능)을 제공한다.

## 10. 디버그 모드 계약 (Executor ↔ Shell 관찰 경계)

디버그 모드는 소스를 **Stmt 단위**로 멈추며 점검하는 대화형 모드다. Executor는 실행 메커니즘을, Shell의 `Debugger`는 대화/명령(Command 패턴)을 담당한다.

### 10-1. Executor 관찰 훅
- `interface ExecutionObserver { void beforeStmt(Stmt stmt, int line, Environment env, int depth); }` (이름·시그니처는 재량이되 다음 정보를 제공): 실행 직전 Stmt, 그 줄번호, 현재 Environment, 블록 중첩 깊이.
- Executor는 각 Stmt 실행 직전 observer에 통지한다. observer가 null이면 일반 실행(오버헤드 0에 가깝게).
- `next`(step over) 구현을 위해 depth(현재 블록 깊이)를 제공한다 — 디버거는 `next` 시 "현재 depth 이하로 다시 내려올 때까지" 통지를 무시한다.
- Environment는 디버거 조회용 API 제공: `Map<String,Object> bindings()`(현 스코프 스냅샷, 읽기전용), `Environment enclosing()`(상위 스코프 노출).

### 10-2. Debugger 명령 (Command 패턴 권장)
Stepping:
| 명령 | 동작 |
|------|------|
| `step` | 현재 Stmt 실행 후 다음 Stmt에서 정지(블록 내부 진입 O) |
| `next` | 현재 Stmt 실행, 블록 내부로 진입하지 않고 같은/상위 레벨 다음 Stmt에서 정지 |
| `break <줄번호>` | 해당 줄에 breakpoint 설정 |
| `breakpoints` | 현재 breakpoint 목록 출력 |
| `remove <줄번호>` | breakpoint 해제 |
| `continue` | 다음 breakpoint까지 실행(없으면 끝까지) |

Watch:
| 명령 | 동작 |
|------|------|
| `watch <변수명>` | 감시 목록 추가. `[WATCH] '<name>' 감시 등록` |
| `unwatch <변수명>` | 감시 목록 제거 |
| `watches` | 감시 변수와 값 출력(가장 인접한 스코프 기준). 정지 시점마다 `[WATCH] <name> = <value>` 자동 출력 |
| `inspect` | 현재 스코프의 모든 변수와 값 출력(`[로컬]`/`[전역]` + 타입) |

출력 라벨: `[DEBUG]`(정지·로딩), `[WATCH]`(감시값), `inspect`는 스코프 헤더 + `[로컬]`/`[전역] <name> = <value> (<Type>)`. PDF 예시 포맷을 따른다.

### 10-3. 진입/종료
- `factory debug <파일>`로 진입 → `[DEBUG] 소스코드 로딩: <파일>` → 첫 Stmt에서 정지 → `[DEBUG] <n>번째 줄에서 정지 → <소스 일부>`.
- breakpoint 정지 시 `[DEBUG] <n>번째 줄에서 정지 (breakpoint) → <소스>`.
- 디버그 명령 입력 루프는 `>` 프롬프트. 실행 완료 시 종료.
