# CodeFab

CodeFab는 Java 17로 작성된 **트리워킹 인터프리터**다. 소스 코드를 세 단계
파이프라인으로 처리한다.

```
source ─▶ Assembler (Scanner ▸ Parser) ─▶ Checker ─▶ Executor ─▶ output
            구문 오류                       정적 오류    런타임 오류
```

- **Assembler**: `Scanner`(텍스트 → 토큰 리스트)와 `Parser`(토큰 → AST). 재귀 하강
  파싱으로 AST(`Expr`/`Stmt`)를 만들고 구문 오류를 진단한다.
- **Checker**: 실행 전 정적 의미 분석 + 리졸버 + 옵티마이저. AST를 DFS로 순회하며
  정적 오류를 진단하고, 정적 바인딩 거리(distance)를 계산하고, 상수 폴딩을 수행한다.
- **Executor**: `Environment`(스코프별 변수 저장소)와 함께 AST를 DFS로 평가한다.

각 단계는 자신이 담당하는 오류 종류(`SCANNER`/`PARSER`/`CHECKER`/`RUNTIME`)만
보고한다. 출력은 `System.out`이 아니라 주입된 `OutputSink`로 흐르므로 테스트가 출력
줄을 직접 수집할 수 있다.

## 빌드 · 실행

Gradle(래퍼 포함)과 JUnit 5를 사용한다.

```bash
./gradlew build       # 컴파일 + 테스트
./gradlew test        # 테스트만 실행
./gradlew installDist # 실행 가능한 배포본 생성
```

실행 진입점은 `codefab.shell.Main`이다. 배포본을 만든 뒤에는 다음으로 실행한다.

```bash
./build/install/codefab/bin/codefab             # 프롬프트(REPL) 모드
./build/install/codefab/bin/codefab run a.txt   # 파일 모드
./build/install/codefab/bin/codefab debug a.txt # 디버그 모드
./build/install/codefab/bin/codefab --help      # 사용법
```

## 공장 제어 쉘 — 3가지 모드

`Main`은 첫 번째 인자로 모드를 선택한다(GoF Strategy).

| 명령 | 모드 | 동작 |
|------|------|------|
| (인자 없음) | 프롬프트(REPL) | 대화식 `PromptShell` |
| `run <파일>` | 파일 모드 | 파일을 한 번 실행 |
| `debug <파일>` | 디버그 모드 | 대화식 디버거로 실행 |
| `<파일>` | 파일 모드 | `run <파일>`과 동일(하위호환) |
| `--help` / `-h` | — | 사용법 출력 |

### 1) 프롬프트 모드 (REPL)

인자 없이 실행한다. 한 줄씩 입력하며, 입력이 완결될 때까지(괄호·중괄호 균형이
맞고 `;` 또는 `}`로 끝날 때까지) 버퍼에 누적한 뒤 실행한다. **전역 변수는 입력 간에
유지된다**(영속 `CodeFabSession`). 종료 명령은 `:exit` 또는 `:quit`다(콜론 접두).

```
CodeFab REPL. Type :exit to quit.
codefab> var a = 5;
codefab> var b = 10;
codefab> print a + b;
15
codefab> {
....... >   var c = a * b;
....... >   print c;
....... > }
50
codefab> :exit
```

### 2) 파일 모드

`run <파일.txt>`(또는 하위호환으로 bare `<파일>`)로 스크립트를 한 번 실행한다.
출력은 표준출력, 진단은 표준에러로 나간다. 런타임 오류는 줄번호와 함께 보고된다.

- 파일을 읽을 수 없으면 `Could not read file '<경로>'`를 출력하고 종료 코드 `66`.
- 진단(구문/정적/런타임)이 발생하면 종료 코드 `65`. 정상 실행은 `0`.

```bash
codefab run script.txt
```

예: `var a = 1;` 다음 줄에 `print 1/0;`이 있으면 표준에러에
`[line 2] RUNTIME error: Division by zero.`가 찍히고 종료 코드는 `65`다(런타임 오류는
`print` 시점에 발생하므로 아무 것도 출력되지 않는다).

### 3) 디버그 모드

`debug <파일.txt>`로 스크립트를 **문(statement) 단위**로 멈추며 실행한다. 디버거는
Executor의 `ExecutionObserver`로 붙어 각 문 직전에 멈출지 결정한다(GoF Command 패턴).
로딩 시 `[DEBUG] 소스코드 로딩: <경로>`를 출력하고 **첫 문에서 멈춘다**. 멈출 때마다
`[DEBUG] N번째 줄에서 정지 → <소스>`를 출력하고 `>` 프롬프트에서 명령을 받는다.

| 명령 | 동작 |
|------|------|
| `step` | 다음 문에서 멈춤(블록·함수 안으로 진입) |
| `next` | 같은 깊이의 다음 문에서 멈춤(블록·함수를 건너뜀) |
| `continue` | breakpoint를 만날 때까지 진행 |
| `break <줄>` | 해당 줄에 breakpoint 설정 |
| `breakpoints` | 설정된 breakpoint 목록 |
| `remove <줄>` | 해당 줄 breakpoint 해제 |
| `watch <변수>` | 변수 감시 등록(멈출 때마다 값 출력) |
| `unwatch <변수>` | 감시 해제 |
| `watches` | 현재 감시 변수들의 값 출력 |
| `inspect` | 현재 스코프의 모든 변수(값·타입) 출력 |

`step`/`next`/`continue`가 입력되면 실행이 재개된다. 명령 스트림이 EOF면 재개로
간주한다. breakpoint에서 멈추면 `(breakpoint)` 표시가 붙는다.

```
[DEBUG] 소스코드 로딩: factory.txt
[DEBUG] 1번째 줄에서 정지 → var a = 3;
> watch a
[WATCH] 'a' 감시 등록
> break 3
[DEBUG] 3번째 줄에 breakpoint 설정
> continue
[WATCH] a = 3
[DEBUG] 3번째 줄에서 정지 (breakpoint) → print a;
> inspect
[DEBUG] 현재 스코프 변수:
[전역] a = 3 (Number)
> step
```

`inspect`는 스코프 체인을 따라 변수를 표시하며, 전역은 `[전역]`, 그 외는 `[로컬]`로
구분한다. 같은 이름이 가려진 경우 가장 가까운 바인딩만 한 번 표시한다.

## 언어 문법

### 값 / 타입

- **number**: 내부적으로 `Double`. 정수와 실수 구분 없음.
- **string**: `"..."` (큰따옴표).
- **boolean**: `true`, `false`.
- **nil**: 초기화되지 않은 변수의 값.
- **함수**: `Func`로 선언한 호출 가능 값.
- **배열**: `Array(n)`이 만드는 고정 크기 배열.

### 변수와 스코프

```
var a = 10;          // 선언 + 초기화
var b;               // 초기화 생략 시 nil
a = a + 1;           // 대입

var x = "global";
{ var x = "inner"; print x; }   // inner  (블록은 자기 스코프를 연다)
print x;                        // global (셰도잉, 바깥 변수는 그대로)
```

전역 스코프 위에 블록마다 새 스코프가 열린다. 변수 탐색은 바깥쪽으로 진행하므로 안쪽
스코프에서 바깥 변수를 읽고·대입하고·가릴(shadow) 수 있다. `for` 루프는 자기 스코프를
가지므로 초기화 변수가 밖으로 새지 않는다.

### 연산자

| 분류 | 연산자 |
|------|--------|
| 산술 | `+` `-` `*` `/` `%` |
| 비교 | `==` `!=` `<` `<=` `>` `>=` |
| 논리 | `and` `or` (단축 평가) |
| 단항 | `-` `+` `!` |

- 우선순위(낮음→높음): `or` → `and` → 동등 → 비교 → `+`/`-` → `*`/`/`/`%` → 단항 → 호출/인덱스.
- **진리값**: `false`와 `nil`만 거짓, 그 외 모든 값은 참.
- `and`/`or`는 단축 평가하며 피연산자 값을 그대로 돌려준다(불리언으로 강제하지 않음).
- `+`는 두 number를 더하거나 두 string을 이어 붙인다. 타입이 섞이면 런타임 오류
  (`Operands must be two numbers or two strings.`).
- 그 외 산술·비교는 두 피연산자가 모두 number여야 한다. `/`와 `%`의 0 나눗셈은 런타임
  오류(`Division by zero.`).
- 단항 `-`/`+`는 number를 요구하고, `!`는 진리값을 반전한다.

**출력 포맷(`print`)**: 정수값 number는 `.0`을 떼고 출력한다(`5.0` → `5`, `3.14` →
`3.14`). string은 따옴표 없이, boolean은 `true`/`false`, nil은 `nil`로 출력한다.

```
print 1 + 2 * 3;              // 7
print (1 + 2) * 3;            // 9
print "Hello, " + "CodeFab!"; // Hello, CodeFab!
print 5.0;                    // 5
print 10 % 3;                 // 1
print true and false;         // false
print nil;                    // nil
```

### 제어문

```
if (a < b) print "less"; else print "not less";

for (var i = 0; i < 3; i = i + 1) print i;   // 0 1 2

var i = 0;
while (i < 3) { print i; i = i + 1; }         // 0 1 2

{ var c = 1; print c; }                       // 블록
```

`if`/`else`, `for`, `while`, 블록 `{}`을 지원한다. `for`의 세 절(초기화·조건·증감)은
각각 생략할 수 있다.

### 함수

`Func`(대문자 F) 키워드로 선언한다. 호출, 매개변수, `return`(값 또는 빈 반환), 재귀를
지원한다. 함수는 선언 시점의 환경을 클로저로 가지며, 자기 이름을 클로저에서 보므로
재귀가 가능하다. `return` 없이 끝나거나 `return;`이면 `nil`을 돌려준다.

```
Func add(a, b) { return a + b; }
print add(3, 7);            // 10

Func fact(n) {
  if (n <= 1) return 1;
  return n * fact(n - 1);
}
print fact(5);              // 120

Func noop() { var x = 1; }
print noop();               // nil
```

### 정적 배열

`Array(n)`은 크기 `n`의 배열을 만든다(`Array`는 키워드가 아니라 전역에 바인딩된
네이티브 함수다). 새 배열의 원소는 모두 `nil`이다. `arr[i]`로 읽고, `arr[i] = v`로
쓴다. 인덱스에는 식을 쓸 수 있다.

```
var arr = Array(3);
arr[0] = 10;
arr[1] = 20;
var i = 2;
arr[i] = 30;
print arr[1];               // 20
print arr[i - 1];           // 20
```

## 진단 / 오류

진단은 단계와(알 때) 줄번호를 가진다. 렌더링은 `[line N] <STAGE> error: <메시지>`
형식이며, 줄번호가 없으면 `<STAGE> error: <메시지>`다. Checker가 진단을 내면
Executor는 실행되지 않는다.

| 단계 | 대표 메시지 예 |
|------|----------------|
| `SCANNER` | `Unexpected character '<c>'.`, `Unterminated string.` |
| `PARSER` | `Expect ';' after value.`, `Expect ')' after arguments.`, `Expect expression.`, `Invalid assignment target.` |
| `CHECKER` | `Already a variable with this name in this scope.`, `Can't read local variable in initializer.`, `Can't return from top-level code.` |
| `RUNTIME` | `Operands must be two numbers or two strings.`, `Operands must be numbers.`, `Division by zero.`, `Can only call functions.`, `Expected N arguments but got M.`, `Can only index arrays.`, `Array index out of bounds.`, `Array index must be a number.`, `Array size must be a number.` |

함수 관련 정적 오류 예: 함수 밖에서 `return`(`Can't return from top-level code.`),
매개변수 이름 중복(`Already a variable with this name in this scope.`). 함수 호출의
인자 개수 불일치와 함수 아닌 값 호출은 런타임 오류다. 배열 인덱스 범위 초과·비수치
인덱스·배열 아닌 대상 인덱싱·비수치 크기는 모두 런타임 오류다.

## 특이사항 (설계 노트)

- **실행 전 최적화 (Checker)**:
  - *정적 바인딩(distance)*: 리졸버가 각 변수 참조가 몇 단계 바깥 스코프에 있는지
    거리(distance)를 미리 계산해 둔다. 실행 시 Executor는 `getAt`/`assignAt`로 해당
    스코프에 곧장 접근하므로 변수 접근이 O(1)이다. 거리가 기록되지 않은 참조는
    전역으로 간주한다.
  - *상수 폴딩(constant folding)*: 실행 전에 값이 완전히 결정되는 부분식(상수 산술·
    비교·논리, 단항 `-`/`!`, 문자열 `+`)을 하나의 리터럴로 접는다. 단, 0으로 나누는
    식은 접지 않고 보존하여 런타임에 `Division by zero.`가 그대로 발생하게 한다(폴딩이
    실행 의미를 바꾸지 않도록).
- **GoF 패턴**: Visitor(AST 순회 — Checker/Executor), Strategy(`CodeFabCallable`,
  쉘 실행 모드 `Mode`), Command(디버그 명령 `DebugCommand`), Interpreter(전체 파이프라인).
- **키워드 대소문자**: `Func`는 대문자 F로 시작한다. `return`/`var`/`print`/`if`/
  `else`/`for`/`while`/`and`/`or`/`true`/`false`는 소문자다. `Array`는 키워드가 아니라
  전역에 정의된 네이티브 함수다.
- **줄 주석**: `//` 부터 줄 끝까지는 주석으로 무시된다.

## 테스트

```bash
./gradlew test
```

기능별 테스트 파일(`src/test/java/codefab/`):

| 파일 | 검증 대상 |
|------|-----------|
| `ScannerTest` | 스캐너(토큰화) |
| `ParserTest` | 파서(구문·AST) |
| `CheckerTest` | 정적 분석·리졸버 |
| `OptimizationTest` | 상수 폴딩·정적 바인딩 최적화 |
| `FunctionTest` | 함수 선언·호출·return·재귀 |
| `ArrayTest` | 정적 배열 생성·인덱스 읽기/쓰기·오류 |
| `WhileTest` | `while` 문 |
| `NormalOperationTest` | 정상 동작 종단 테스트 |
| `ErrorTest` | 단계별 오류 종단 테스트 |
| `SessionTest` | 세션(전역 변수 유지) |
| `PromptShellTest` | REPL 셸 |
| `FileModeTest` | 파일 모드(줄번호 런타임 오류, 종료 코드) |
| `DebugModeTest` | 디버그 모드(stepping·breakpoint·watch·inspect) |
