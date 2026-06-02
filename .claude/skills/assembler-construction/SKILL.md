---
name: assembler-construction
description: "트리워킹 인터프리터의 Assembler 유닛(Scanner + Parser + Expr/Stmt AST) 구현. 소스를 토큰 리스트로 스캔, 재귀 하강 파서로 AST 생성, 연산자 우선순위 처리, 구문 오류 진단과 에러 복구(동기화), 새 토큰·키워드·문법 규칙·AST 노드 추가 작업에 사용. CodeFab 인터프리터의 assembler-engineer가 사용. 스캐너/파서/문법/토큰/AST 작업이면 이 스킬을 사용할 것."
---

# Assembler Construction — Scanner · Parser · AST

소스 텍스트를 토큰으로, 토큰을 AST로 변환한다. 구문 오류는 이 유닛에서만 진단한다. 의미·런타임 오류는 다루지 않는다(각각 Checker·Executor의 책임).

## 작업 시작 전

`references/shared-contracts.md`를 읽는다. Token/TokenType/Expr/Stmt/Diagnostic의 정확한 시그니처와 고정 진단 문자열이 거기 있다. AST 노드를 추가·변경하면 먼저 팀에 통지하고 계약 파일을 갱신한다.

## AST 설계 불변식

- **Expr는 Stmt를 자식으로 갖지 않는다.** Expr 필드는 Expr·Token만. Stmt 필드는 Expr·Stmt·Token.
- Token은 노드가 아니라 노드의 필드다. 연산자·이름은 Token으로 보존해야 진단에서 줄 번호를 낼 수 있다.
- 순회는 방문자 패턴으로 한다(`accept(Visitor<R>)`). Checker/Executor가 노드 정의에 결합되지 않게 한다.

## Scanner

문자를 한 번에 하나씩 소비하며 토큰을 누적한다. 어휘 오류는 던지지 않고 진단에 추가하여 항상 EOF까지 도달한다(에러 복구를 위해).

핵심 규칙:
- 공백/`\r`/`\t`는 무시, `\n`에서 줄 번호 증가, 문자열 내부 개행도 줄 번호 증가.
- `//`는 줄 끝까지 주석으로 건너뛴다. `/`는 주석이 아니면 SLASH.
- 1자/2자 토큰은 `match('=')` 선읽기로 구분(`!`/`!=`, `=`/`==`, `<`/`<=`, `>`/`>=`).
- 문자열은 `"` 기반. 닫히지 않으면 `Unterminated string.` 진단. literal은 따옴표를 벗긴 내용.
- 숫자는 `double`로 저장. 소수부는 `.` 뒤에 숫자가 올 때만 소비(`peekNext`로 확인).
- 식별자는 `[A-Za-z_][A-Za-z0-9_]*`. 키워드 맵으로 IDENTIFIER와 구분(`and or if else true false for var print`).
- 그 외 문자는 `Unexpected character '<c>'.` 진단.

## Parser — 재귀 하강

문법의 각 비단말을 메서드로 옮긴다. 우선순위는 호출 깊이로 표현된다(낮은 우선순위가 바깥). CodeFab 문법 체인:

```
expression → assignment → or → and → equality → comparison → term → factor → unary → primary
```

이항 연산은 좌결합 루프로 구현한다:
```java
private Expr term() {
    Expr expr = factor();
    while (match(MINUS, PLUS)) {           // 좌결합: 누적해 나간다
        Token op = previous();
        Expr right = factor();
        expr = new Expr.Binary(expr, op, right);
    }
    return expr;
}
```

**대입은 우결합이며 LHS를 먼저 파싱한 뒤 검증한다.** `or()`로 좌변을 파싱하고, `=`가 오면 `assignment()`를 재귀 호출해 값을 얻은 뒤 좌변이 `Variable`인지 본다. 아니면 `Invalid assignment target.`을 보고하되 **던지지 않고** 복구한다(좌변은 이미 유효하게 파싱됨).

**제어문**: `ifStmt`는 then/else를 `statement()`로 받는다. else는 항상 가장 가까운 if에 결합되므로 별도 처리 없이 재귀 구조가 dangling-else를 올바르게 해결한다. `forStmt`는 초기화/조건/증가가 모두 생략 가능(`( varDecl | exprStmt | ";" ) expr? ";" expr? )`)하며, 비어 있으면 null을 채운다.

## 진단과 에러 복구

오류는 `Diagnostic`(Stage.PARSER)으로 수집한다. `consume(type, msg)`가 실패하면 `ParseError`(내부 제어 신호)를 던지고, `declaration()`의 try/catch가 잡아 `synchronize()`로 다음 문 경계까지 토큰을 버린다. 이렇게 한 오류가 연쇄 오류를 만들지 않는다.

- 메시지는 고정 문자열을 정확히 포함시킨다(`Expect ';' after value.` 등 — 계약 파일 참조).
- 위치 표시(`" at '<lexeme>'"`/`" at end"`)는 덧붙여도 되지만 고정 부분 문자열을 깨지 않는다.
- `synchronize()`는 SEMICOLON 직후 또는 다음 문 시작 키워드(VAR/FOR/IF/PRINT)에서 멈춘다.

## 출력

`codefab.core`에 Token/TokenType/Expr/Stmt, `codefab.assembler`에 Scanner/Parser/ParseError를 둔다. 생성자는 진단 리스트를 주입받아(`List<Diagnostic>`) 호출자가 단계별 오류를 모으게 한다.
