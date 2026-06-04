# CodeFab

> 직접 만든 언어를 실행하는 트리워킹 인터프리터

CodeFab는 Java로 구현된 커스텀 스크립팅 언어 인터프리터입니다.
소스 코드를 스캔(Scanner) → 파싱(Parser) → 정적 분석(Checker) → 실행(Executor) 의 4단계 파이프라인을 거쳐 실행합니다.

---

## 목차

1. [빠른 시작](#빠른-시작)
2. [설치 및 빌드](#설치-및-빌드)
3. [실행 방법](#실행-방법)
4. [언어 문법](#언어-문법)
   - [기본 타입](#기본-타입)
   - [변수](#변수)
   - [출력](#출력)
   - [산술 연산](#산술-연산)
   - [비교 및 논리 연산](#비교-및-논리-연산)
   - [제어문 (if / else)](#제어문-if--else)
   - [반복문 (while / for)](#반복문-while--for)
   - [블록과 스코프](#블록과-스코프)
   - [주석](#주석)
5. [예제 프로그램](#예제-프로그램)
6. [에러 처리](#에러-처리)
7. [아키텍처](#아키텍처)
8. [테스트](#테스트)
9. [코드리뷰](#코드리뷰)

---

## 빠른 시작

```bash
# 프로젝트 클론 후 빌드
git clone <repository-url>
cd CodeFab
./gradlew build

# 대화형 REPL 실행
./gradlew run --console=plain
```

```
CodeFab REPL. Type :exit to quit.
codefab> print "Hello, CodeFab!";
Hello, CodeFab!
codefab> var x = 10; print x * 2;
20
codefab> :exit
```

---

## 설치 및 빌드

**요구 사항**

| 항목 | 버전 |
|------|------|
| JDK  | 17 이상 |
| Gradle | Wrapper 포함 (별도 설치 불필요) |

```bash
# 빌드 (컴파일 + 테스트)
./gradlew build

# 컴파일만
./gradlew compileJava
```

---

## 실행 방법

### 대화형 REPL

인자 없이 실행하면 대화형 셸(REPL)이 열립니다.
세미콜론 `;` 또는 닫는 중괄호 `}` 로 끝나는 완전한 문장이 입력되면 즉시 실행됩니다.

```bash
./gradlew run --console=plain
```

| REPL 명령어 | 설명 |
|-------------|------|
| `:exit`     | REPL 종료 |
| `:quit`     | REPL 종료 (`:exit` 와 동일) |
| `:env`      | 환경 정보 표시 |

### 스크립트 파일 실행

`.cfab` 확장자(혹은 임의 텍스트 파일)로 저장한 스크립트를 실행합니다.

```bash
./gradlew run --args="hello.cfab"
```

또는 빌드 후 생성되는 실행 파일로 실행합니다.

```bash
./build/scripts/CodeFab hello.cfab
```

**도움말 출력**

```bash
./build/scripts/CodeFab --help
```

---

## 언어 문법

### 기본 타입

CodeFab 는 네 가지 기본 값 타입을 지원합니다.

| 타입 | 예시 | 설명 |
|------|------|------|
| 숫자 | `42`, `3.14`, `-7` | 정수 및 실수 (내부적으로 `double` 처리) |
| 문자열 | `"Hello"` | 큰따옴표로 감싼 텍스트 |
| 불리언 | `true`, `false` | 참/거짓 |
| nil | `nil` | 초기화되지 않은 변수의 기본값 |

```
print 42;        // 42
print 3.14;      // 3.14
print "CodeFab"; // CodeFab
print true;      // true
print false;     // false
```

> **숫자 출력 규칙**: 소수점 이하가 0이면 정수로 출력합니다. (`5.0` → `5`)

---

### 변수

`var` 키워드로 변수를 선언합니다. 선언과 동시에 초기화할 수 있으며, 초기화하지 않으면 `nil` 이 됩니다.
모든 문장은 세미콜론 `;` 으로 끝나야 합니다.

```
// 선언과 동시에 초기화
var name = "CodeFab";
var version = 1;
var pi = 3.14;
var isReady = true;

// 선언만 (값은 nil)
var empty;

// 재할당
var count = 0;
count = count + 1;

print name;    // CodeFab
print version; // 1
print empty;   // nil
print count;   // 1
```

---

### 출력

`print` 키워드로 값을 표준 출력에 출력합니다. 한 번에 하나의 값을 출력합니다.

```
print "안녕하세요!";
print 100;
print true;

// 변수 출력
var msg = "Hello";
print msg;

// 표현식 결과 출력
print 3 + 4;          // 7
print "A" + "B";      // AB (문자열 연결)
```

---

### 산술 연산

숫자끼리의 산술 연산을 지원합니다. `+` 는 문자열 연결에도 사용됩니다.

| 연산자 | 기능 | 예시 |
|--------|------|------|
| `+` | 덧셈 / 문자열 연결 | `3 + 2` → `5`, `"a" + "b"` → `ab` |
| `-` | 뺄셈 / 부호 반전 | `5 - 3` → `2`, `-7` |
| `*` | 곱셈 | `4 * 3` → `12` |
| `/` | 나눗셈 | `10 / 4` → `2.5` |

```
print 1 + 2 * 3;      // 7  (곱셈 우선)
print (1 + 2) * 3;    // 9  (괄호 우선)
print 10 - 4 - 3;     // 3  (왼쪽부터)
print 8 / 2 / 2;      // 2  (왼쪽부터)
print -3 + 2;         // -1 (단항 부호 반전)
print "Hello, " + "World!"; // Hello, World!
```

---

### 비교 및 논리 연산

**비교 연산자** (숫자에만 적용 가능, 결과는 불리언)

| 연산자 | 기능 | 예시 |
|--------|------|------|
| `==` | 동등 비교 | `1 == 1` → `true` |
| `!=` | 불일치 비교 | `1 != 2` → `true` |
| `>`  | 초과 | `3 > 2` → `true` |
| `>=` | 이상 | `3 >= 3` → `true` |
| `<`  | 미만 | `2 < 5` → `true` |
| `<=` | 이하 | `4 <= 4` → `true` |

**논리 연산자** (단락 평가)

| 연산자 | 기능 | 예시 |
|--------|------|------|
| `and` | 논리 AND | `true and false` → `false` |
| `or`  | 논리 OR  | `true or false` → `true` |
| `!`   | 논리 NOT | `!true` → `false` |

```
print 1 < 2;            // true
print 3 > 5;            // false
print 1 == 1;           // true
print "hi" != "bye";    // true

print true and false;   // false
print true or false;    // true
print !false;           // true

// 단락 평가: 왼쪽 결과만으로 확정되면 오른쪽은 평가하지 않음
print true or false;    // true  (오른쪽 무시)
print false and true;   // false (오른쪽 무시)
```

---

### 제어문 (if / else)

```
if (조건) 문장;

if (조건) {
    문장들;
} else {
    문장들;
}
```

```
var score = 85;

if (score >= 90) {
    print "A";
} else if (score >= 80) {
    print "B";
} else {
    print "C";
}
// 출력: B

// else 없는 if
if (true) print "실행됨";

// 중첩 if (dangling else 는 가장 가까운 if 에 결합)
if (true)
    if (false) print "never";
    else print "inner else"; // 안쪽 if 의 else
```

---

### 반복문 (while / for)

#### while

```
while (조건) {
    문장들;
}
```

```
var i = 0;
while (i < 5) {
    print i;
    i = i + 1;
}
// 출력: 0 1 2 3 4
```

#### for

```
for (초기화; 조건; 증감) {
    문장들;
}
```

```
for (var j = 0; j < 3; j = j + 1) {
    print j;
}
// 출력: 0 1 2

// 세 항목 모두 선택 사항
var k = 0;
for (; k < 3; k = k + 1) {
    print k;
}
```

> **주의**: `for` 루프의 초기화 변수는 루프 블록 내에서만 유효합니다. 루프 바깥에서는 접근할 수 없습니다.

---

### 블록과 스코프

중괄호 `{}` 로 새로운 스코프를 만들 수 있습니다.
안쪽 블록은 바깥 변수에 접근할 수 있지만, 안쪽에서 선언한 변수는 블록이 끝나면 사라집니다.
같은 이름의 변수를 안쪽 블록에서 다시 선언하면 외부 변수를 가립니다 (섀도잉).

```
var x = "전역";
{
    var x = "내부";  // 전역 x 를 가림
    print x;         // 내부
}
print x;             // 전역

// 외부 변수 수정
var count = 0;
{
    count = count + 1;  // 외부 count 를 수정
}
print count;  // 1

// 중첩 블록
var outer = "A";
{
    var inner = "B";
    {
        print outer + inner;  // AB
    }
    // inner 는 여기서도 유효
}
// inner 는 여기서 접근 불가
```

---

### 주석

`//` 이후 같은 줄은 주석으로 처리되어 무시됩니다.

```
// 이 줄 전체가 주석입니다

var x = 42; // 인라인 주석
print x;    // 42
```

---

## 예제 프로그램

### 1. 1부터 10까지 합계

```
var sum = 0;
for (var i = 1; i <= 10; i = i + 1) {
    sum = sum + i;
}
print sum;  // 55
```

### 2. 짝수/홀수 판별

```
var n = 7;
if (n / 2 * 2 == n) {
    print "짝수";
} else {
    print "홀수";
}
// 홀수
```

### 3. 피보나치 수열 (처음 8개)

```
var a = 0;
var b = 1;
var count = 0;

while (count < 8) {
    print a;
    var temp = b;
    b = a + b;
    a = temp;
    count = count + 1;
}
// 0 1 1 2 3 5 8 13
```

### 4. 구구단 (3단)

```
for (var i = 1; i <= 9; i = i + 1) {
    print 3 * i;
}
// 3 6 9 12 15 18 21 24 27
```

### 5. 카운트다운

```
var n = 5;
while (n > 0) {
    print n;
    n = n - 1;
}
print "발사!";
// 5 4 3 2 1 발사!
```

### 6. 문자열 반복 출력

```
var line = "=";
var result = "";
for (var i = 0; i < 5; i = i + 1) {
    result = result + line;
}
print result;  // =====
```

---

## 에러 처리

CodeFab 는 오류를 단계별로 구분하여 정확한 위치와 원인을 알려줍니다.

| 단계 | 오류 종류 | 예시 |
|------|----------|------|
| `SCANNER` | 인식 불가 문자 | 허용되지 않는 특수문자 사용 |
| `PARSER` | 구문 오류 | 세미콜론 누락, 괄호 불일치 |
| `CHECKER` | 정적 분석 오류 | 미선언 변수 사용, 같은 스코프 중복 선언 |
| `RUNTIME` | 런타임 오류 | 0으로 나누기, 타입 불일치 |

**오류 메시지 형식**

```
[line 3] PARSER error: Expected ';' after value at ';'
[line 5] CHECKER error: undefined variable 'x'
[line 7] RUNTIME error: Division by zero.
```

**주요 에러 예시**

```
// PARSER - 세미콜론 누락
print 1 + 2     // 오류: ';' 필요

// PARSER - 괄호 누락
print (1 + 2;   // 오류: ')' 필요

// CHECKER - 미선언 변수
print unknown;  // 오류: undefined variable 'unknown'

// CHECKER - 같은 스코프 중복 선언
{
    var a = 1;
    var a = 2;  // 오류: 이미 선언된 변수
}

// CHECKER - 자기 초기화자에서 자신 참조
{
    var a = a;  // 오류: 초기화 중 자신 참조 불가
}

// RUNTIME - 0으로 나누기
print 10 / 0;   // 오류: Division by zero.

// RUNTIME - 타입 불일치
print 1 + "hello";  // 오류: 숫자와 문자열 혼합 불가
print -"text";      // 오류: 문자열에 부호 연산 불가
```

---

## 아키텍처

```
소스 코드 (String)
      │
      ▼
┌─────────────┐
│   Scanner   │  소스 문자열 → 토큰 목록
│             │  (어휘 분석 / Lexer)
└──────┬──────┘
       │ List<Token>
       ▼
┌─────────────┐
│   Parser    │  토큰 목록 → AST (추상 구문 트리)
│             │  (구문 분석)
└──────┬──────┘
       │ List<Stmt>
       ▼
┌─────────────┐
│   Checker   │  AST 정적 분석
│             │  (변수 선언 여부, 스코프 검사)
└──────┬──────┘
       │ List<Stmt> (오류 없을 때만)
       ▼
┌─────────────┐
│  Executor   │  AST 순회 및 실행
│             │  (트리워킹 인터프리터)
└──────┬──────┘
       │ RunResult
       ▼
  출력 + 진단 메시지
```

**주요 클래스 구조**

```
codefab/
├── CodeFab.java            진입점 (단일 run() 메서드)
├── CodeFabSession.java     파이프라인 조립 및 상태 유지 (REPL 세션)
├── RunResult.java          실행 결과 (성공 여부, 출력, 진단)
├── assembler/
│   ├── Scanner.java        어휘 분석 (소스 → 토큰)
│   └── Parser.java         구문 분석 (토큰 → AST)
├── checker/
│   └── Checker.java        정적 의미 분석
├── executor/
│   ├── Executor.java       AST 실행 (Visitor 패턴)
│   └── Environment.java    변수 스코프 체인
├── core/
│   ├── Expr.java           표현식 AST 노드
│   ├── Stmt.java           문장 AST 노드
│   ├── Token.java          토큰
│   ├── TokenType.java      토큰 종류
│   └── Diagnostic.java     오류 진단 메시지
└── shell/
    ├── Main.java            CLI 진입점
    └── PromptShell.java     대화형 REPL 셸
```

---

## 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 테스트 결과 리포트 확인 (HTML)
open build/reports/tests/test/index.html
```

테스트는 다음 4개 계층으로 구성됩니다.

| 테스트 클래스 | 범위 |
|--------------|------|
| `ScannerTest` | 어휘 분석 단위 테스트 |
| `ParserTest` | 구문 분석 단위 테스트 |
| `CheckerTest` | 정적 분석 단위 테스트 |
| `ExecutorTest` | 실행 단위 테스트 |
| `EndToEndTest` | 전체 파이프라인 통합 테스트 |
| `PromptShellTest` | REPL 동작 테스트 |

---

## 코드리뷰

CodeFab은 C, C++, C#, Java, JavaScript 같은 주류 언어에서는 허용되지만 **의도적으로 막아 둔** 코드 패턴이 있다.
단순한 기능 부재가 아니라, 각 제약에는 명확한 설계 의도가 있다.

### 1. 초기화자 내 자기 참조

```
{ var a = 3; { var a = a + 3; } }
```

`Checker` 에러: `Can't read local variable in initializer.`

| 언어 | 동작 결과 | 에러 여부 |
|------|-----------|-----------|
| JavaScript (`var`) | 블록 스코프 없음 — 내부 `var a`가 같은 변수 재선언, `a = NaN` (`undefined + 3`) | 에러 없음 (의도와 다른 결과) |
| JavaScript (`let`) | 내부 `a`는 TDZ(Temporal Dead Zone) — 우변에서 읽는 순간 `ReferenceError` | 런타임 에러 |
| C / C++ | 내부 `a`가 새로 선언됨. 우변의 `a`는 초기화되지 않은 자신을 읽음 → 미정의 동작(UB) | 컴파일 됨 (경고 발생 가능) |
| Java / C# | 메서드 내부에서 같은 이름으로 섀도잉 자체를 금지 | 컴파일 에러 |
| **CodeFab** | **정적 분석 단계에서 즉시 거부** | **CHECKER 에러** |

`var a = a + 3`의 우변 `a`가 **"새로 선언되는 자기 자신"** 인지 **"바깥 스코프의 `a`"** 인지 프로그래머 입장에서 모호하다.
CodeFab은 `initializingVar` 필드로 현재 초기화 중인 변수 이름을 추적하고, 초기화자 안에서 그 이름이 등장하면 즉시 에러로 처리한다.
바깥 `a`가 존재하더라도 예외 없이 차단함으로써, "의미 있어 보이지만 실제로는 위험한 코드"를 컴파일 타임에 제거한다.

### 2. 같은 스코프 내 변수 재선언 금지

```
var x = 1;
var x = 2;
```

`Checker` 에러: `Already a variable with this name in this scope.`

| 언어 | 동작 결과 | 에러 여부 |
|------|-----------|-----------|
| JavaScript (`var`) | 재선언 허용 — 조용히 덮어씀, `x = 2` | 에러 없음 (의도치 않은 덮어쓰기 위험) |
| JavaScript (`let` / `const`) | `SyntaxError: Identifier 'x' has already been declared` | 구문 에러 |
| C / C++ | `error: redeclaration of 'x'` | 컴파일 에러 |
| Java | `error: variable x is already defined` | 컴파일 에러 |
| C# | `error CS0136: A local variable named 'x' is already defined` | 컴파일 에러 |
| **CodeFab** | **정적 분석 단계에서 즉시 거부** | **CHECKER 에러** |

재선언은 오타나 복사-붙여넣기 실수에서 비롯되는 경우가 대부분이며, 의도적으로 재선언하는 경우는 극히 드물다.
JavaScript `var`처럼 재선언을 조용히 허용하면 기존 값이 덮어써져 디버깅이 어려운 버그로 이어진다.
CodeFab은 스코프당 선언된 이름을 `Set<String>`으로 관리하고, 같은 스코프에서 이름이 충돌하면 즉시 거부함으로써 이 클래스의 버그를 원천 차단한다.

### 3. 미선언 변수 대입 금지

```
y = 5;
```

앞에 `var y` 선언이 없을 때.

`Checker` 에러: `undefined variable 'y'`

| 언어 | 동작 결과 | 에러 여부 |
|------|-----------|-----------|
| JavaScript (비엄격 모드) | 전역 객체(`window`)에 암묵적으로 `y` 속성 생성 → 암묵적 전역 | 에러 없음 (심각한 오염 위험) |
| JavaScript (`"use strict"`) | `ReferenceError: y is not defined` | 런타임 에러 |
| C / C++ | `error: 'y' was not declared in this scope` | 컴파일 에러 |
| Java | `error: cannot find symbol` | 컴파일 에러 |
| C# | `error CS0103: The name 'y' does not exist in the current context` | 컴파일 에러 |
| **CodeFab** | **정적 분석 단계에서 즉시 거부** | **CHECKER 에러** |

JavaScript 비엄격 모드의 암묵적 전역은 오타 하나가 전역 상태를 오염시키는 악명 높은 함정이다.
CodeFab은 `var`을 통한 명시적 선언만을 허용하고, 선언 없이 변수를 읽거나 대입하려 하면 `checkDeclared`가 모든 스코프를 탐색한 뒤 없으면 에러를 발생시킨다.
이는 대입(`=`) 연산에도 예외 없이 적용된다 — 읽기뿐 아니라 쓰기도 반드시 사전 선언이 필요하다.

### 4. 숫자와 문자열의 암묵적 형변환 금지

```
print "나이: " + 5;
```

`Runtime` 에러: `Operands must be two numbers or two strings.`

| 언어 | 동작 결과 | 에러 여부 |
|------|-----------|-----------|
| JavaScript | `"나이: 5"` — 숫자를 문자열로 자동 변환 (`1 + "1" === "11"` 혼란 유발) | 에러 없음 |
| Java | `"나이: 5"` — `String + int` 자동 변환 (컴파일러가 `StringBuilder`로 처리) | 에러 없음 |
| C# | `"나이: 5"` — `String.Concat`으로 처리 | 에러 없음 |
| C | 불가 — 문자열과 정수의 `+` 자체가 포인터 연산 | 의도치 않은 포인터 연산 |
| C++ | `std::string + int` 불가 — `std::to_string(5)` 필요 | 컴파일 에러 |
| **CodeFab** | **런타임 단계에서 즉시 거부** | **RUNTIME 에러** |

JavaScript의 `+` 연산자는 좌우 피연산자에 따라 덧셈과 문자열 이어붙이기를 오가며 혼란을 일으킨다 (`1 + "1" === "11"`, `"1" + 1 === "11"`, `1 - "1" === 0`).
CodeFab의 `+`는 **숫자 + 숫자** 또는 **문자열 + 문자열** 중 하나만 허용한다.
혼합 연산이 필요하다면 명시적 변환을 통해 의도를 드러내야 한다는 원칙을 따른다.

### 제약사항 요약

| # | 예시 코드 | 차단 단계 | 에러 메시지 |
|---|-----------|-----------|-------------|
| 1 | `{ var a = 3; { var a = a + 3; } }` | CHECKER | `Can't read local variable in initializer.` |
| 2 | `var x = 1; var x = 2;` | CHECKER | `Already a variable with this name in this scope.` |
| 3 | `y = 5;` (미선언) | CHECKER | `undefined variable 'y'` |
| 4 | `"나이: " + 5` | RUNTIME | `Operands must be two numbers or two strings.` |

**CHECKER** 에러는 실행 전 정적 분석 단계에서 잡히므로, 에러가 있으면 Executor는 전혀 실행되지 않는다.
**RUNTIME** 에러는 실제 실행 중에 해당 표현식이 평가될 때 발생한다.

---

## 언어 문법 요약표

| 구문 | 형식 | 예시 |
|------|------|------|
| 변수 선언 | `var 이름 = 값;` | `var x = 10;` |
| 변수 재할당 | `이름 = 값;` | `x = x + 1;` |
| 출력 | `print 표현식;` | `print x;` |
| 주석 | `// 내용` | `// 설명` |
| 조건문 | `if (조건) {...} else {...}` | `if (x > 0) {...}` |
| while 반복 | `while (조건) {...}` | `while (i < 10) {...}` |
| for 반복 | `for (초기화; 조건; 증감) {...}` | `for (var i=0; i<3; i=i+1) {...}` |
| 블록 | `{ 문장들; }` | `{ var a = 1; }` |
| 문자열 | `"텍스트"` | `"Hello"` |
| 불리언 | `true` / `false` | `var ok = true;` |
| 논리 AND | `and` | `a and b` |
| 논리 OR | `or` | `a or b` |
| 논리 NOT | `!` | `!true` |
