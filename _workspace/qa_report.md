# CodeFab QA 경계면 정합성 리포트 (함수·배열·최적화·디버그 쉘)

날짜: 2026-06-05 · 검증자: qa-verifier · 기준: shared-contracts.md
방법: 생산자/소비자 양쪽 동시 읽기 + §7 고정 문자열 grep + `./gradlew test --rerun-tasks`

## 종합 결과

- **테스트: 99개 전부 통과 (failures+errors = 0).**
  - ArrayTest 8, CheckerTest 6, DebugModeTest 6, ErrorTest 13, FileModeTest 4,
    FunctionTest 11, NormalOperationTest 15, OptimizationTest 7, ParserTest 6,
    PromptShellTest 4, ScannerTest 9, SessionTest 4, WhileTest 6.
- **경계면 이슈: 0건.** 4개 신규 기능군(함수·정적 배열·실행 전 최적화·공장 제어 쉘)
  의 생산자/소비자 계약이 모두 일치.

## 항목별 판정 (통과 / 실패 / 미검증)

### 1. AST 계약 — 통과
- Parser가 생성하는 Call(callee, paren, arguments)·Index(target, bracket, index)·
  IndexSet(target, bracket, index, value)·FunctionStmt(name, params, body)·
  ReturnStmt(keyword, value) 필드가 Expr.java/Stmt.java 정의 및 Checker·Executor
  visit 소비와 정확히 일치.
- `assignment()`가 `arr[i] = v`에서 Index를 IndexSet으로 올바르게 재작성
  (Parser.java:193-196).
- Stmt.line 일관성: 모든 Stmt가 `super(line)`로 줄번호 보유. Parser가 시작 토큰
  기준으로 채움(Func/var/print/if/for/while=키워드 줄, return=keyword.line,
  expressionStmt=peek().line, block=`{`줄). ConstantFolder가 새 노드 생성 시
  s.line을 그대로 전파 → 폴딩 후에도 줄번호 보존.
- nullable 필드(VarStmt.initializer, IfStmt.elseBranch, ForStmt 3절,
  ReturnStmt.value) 모두 Checker·Executor·ConstantFolder에서 null 가드 확인.

### 2. distance 맵 (노드 동일성) — 통과
- Checker.check()는 **먼저 ConstantFolder.fold()로 폴딩한 트리를 만든 뒤 그
  폴딩된 트리를 resolve**한다(Checker.java:58-64). 따라서 locals 키와
  CheckResult.program() 노드가 **동일 객체**. IdentityHashMap 사용(Checker.java:44).
- Executor.visitVariable/visitAssign이 `locals.get(expr)`로 조회하는 expr은
  실행 중인 폴딩 트리 노드와 동일 → 옛 노드 조회 버그 없음.
- 전역/네이티브 Array/이전 REPL run 변수 폴백: distance 없으면(resolved=true)
  `globals.get(name)`로 폴백(Executor.java:270-273). resolveLocal이 스코프에서
  못 찾으면 맵에 안 넣음(Checker.java:269-277) → 네이티브 Array 등이 폴백됨.
  OptimizationTest.unresolvableReferencesAreAbsentFromLocals가 검증.
- 주의(계약 대비 양성 편차, 버그 아님): Checker는 최상위를 명시적 스코프 프레임으로
  모델링하므로 top-level var는 맵에 distance와 함께 들어간다(§9-1 문구 "전역은 맵에서
  제외"와 다름). Executor의 globals가 바로 그 프레임이라 distance 접근이 같은 바인딩에
  도달 → 의미 동일. OptimizationTest 주석에 문서화됨. 미수정 권고(의도된 설계).

### 3. 폴딩 안전 (/0·%0) — 통과
- ConstantFolder.applyBinary: SLASH/PERCENT에서 `b==0.0`이면 `null` 반환 →
  해당 부분식 폴딩 안 함, 원본 유지(ConstantFolder.java:177-182).
- Executor.visitBinary가 런타임에 `Division by zero.` 보고(Executor.java:345-354).
- OptimizationTest.divisionByZeroIsNotFoldedAwayAndStillFaultsAtRuntime 통과로
  RUNTIME Stage + 메시지 + print 미출력까지 확인.

### 4. 함수/배열 런타임 메시지 + Stage — 통과
- §7 고정 문자열 grep 결과 모두 Executor에서 InterpreterRuntimeError로 발생
  (→ Facade/Debugger가 RUNTIME 진단으로 변환):
  - `Can only call functions.` Executor.java:381
  - `Expected <n> arguments but got <m>.` Executor.java:390 (arity/size 보간)
  - `Can only index arrays.` :421, `Array index must be a number.` :426,
    `Array index out of bounds.` :430, `Array size must be a number.` :108/114
- 함수 외부 return·파라미터 중복은 CHECKER:
  - `Can't return from top-level code.` Checker.java:139 (Stage.CHECKER)
  - 파라미터 중복은 declare() 재사용 → `Already a variable with this name in this
    scope.` Checker.java:250 (Stage.CHECKER)
- 테스트 Stage 단언 일치: FunctionTest(returnOutsideFunction=CHECKER,
  duplicateParameter=CHECKER, callingNonFunction=RUNTIME, argCountMismatch=RUNTIME),
  ArrayTest(4종 모두 RUNTIME).

### 5. 디버그 훅 — 통과
- Executor.execute(Stmt)가 모든 Stmt 실행 직전 단일 지점에서 observer 통지
  (Executor.java:132-139). observer==null이면 통지·depth 증감 전부 스킵 → 오버헤드
  사실상 0.
- depth 증감: executeBlock(블록·함수 호출 프레임 공통, :219-231)과 visitForStmt
  (:190-201)에서 `if(observer!=null) depth++/--`. 함수 호출은 CodeFabFunction.call
  → executor.executeBlock 경유(CodeFabFunction.java:37)이므로 함수 본문도 depth+1 →
  `next`가 함수/블록에 안 들어감.
- Debugger.shouldStop의 NEXT는 `depth <= nextBaselineDepth`, baseline은 직전
  beforeStmt의 lastDepth(Debugger.java:150-161, 267-269) → step over 정확.
  DebugModeTest.nextStepsOverBlockWithoutEnteringIt 통과.
- Environment.bindings()(불변 복사)·enclosing() 사용을 Debugger.lookup/inspect가
  올바르게 사용(Debugger.java:329-375).

### 6. 파이프라인 단락 + REPL 세션 — 통과
- CodeFabSession.run: Scanner→Parser → 진단 있으면 중단 → Checker.check(폴딩+locals)
  → 진단 있으면 중단(실행 금지) → setLocals(checked.locals()) 후
  execute(checked.program()) → 런타임 오류는 RUNTIME 진단 변환
  (CodeFabSession.java:35-67). 폴딩된 program() 실행 확인(원본 미실행).
- 세션 상태: globals·executor는 run 간 유지(필드), output은 run 시작 시 clear(),
  Checker는 run마다 new(CodeFabSession.java:26-32, 36, 49). distance 도입 후에도
  setLocals로 run별 맵 교체 + globals 폴백으로 이전 run 변수 보존. SessionTest 통과.
- Debugger.run도 동일 순서로 checked.program()/locals() 실행(Debugger.java:99-127).

## 미검증 / 한계
- 성능(observer null일 때 실측 오버헤드)은 코드 경로 분석으로만 확인(벤치마크 미수행).
- 동시성·대용량 입력은 범위 외.

## 권고
- 수정 필요 이슈 없음. 항목 2의 top-level distance 편차는 의미가 동일하고
  테스트·주석으로 문서화되어 있어 현행 유지 권고.
