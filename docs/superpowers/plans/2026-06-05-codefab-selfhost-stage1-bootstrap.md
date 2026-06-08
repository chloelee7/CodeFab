# CodeFab Selfhost Stage 1 Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the minimal native bootstrap functions CodeFab needs to write a scanner/parser in CodeFab: `len`, `charAt`, `slice`, `push`, `chr`, `ord`, and `num`.

**Architecture:** Introduce a small `CodeFabCallable` runtime abstraction so user functions and native functions share one call path. Preserve current `Array(n)` behavior while replacing the executor sentinel with a native callable registered in the global environment.

**Tech Stack:** Java 17, Gradle, JUnit 5, CodeFab tree-walking interpreter.

---

## File Structure

- Create: `src/test/java/codefab/NativeFunctionTest.java`
  - End-to-end tests for new native functions and preserved `Array(n)` behavior.
- Create: `src/main/java/codefab/executor/CodeFabCallable.java`
  - Package-private callable interface shared by user and native functions.
- Create: `src/main/java/codefab/executor/NativeFunction.java`
  - Package-private base class for native functions.
- Modify: `src/main/java/codefab/executor/CodeFabFunction.java`
  - Implement `CodeFabCallable`.
- Modify: `src/main/java/codefab/executor/Executor.java`
  - Register native functions, remove the `ARRAY_BUILTIN` sentinel, route calls through `CodeFabCallable`, and add type/index helpers.
- Modify: `_workspace/codefab-selfhost/progress.md`
  - Record Stage 1 RED/GREEN/full-verification evidence after execution.

## Task 1: RED Tests for Bootstrap Functions

**Files:**
- Create: `src/test/java/codefab/NativeFunctionTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/codefab/NativeFunctionTest.java`:

```java
package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codefab.core.Diagnostic;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NativeFunctionTest {

    private static RunResult run(String source) {
        return new CodeFab().run(source);
    }

    private static List<String> output(String source) {
        RunResult result = run(source);
        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        return result.output();
    }

    private static void assertRuntimeError(String source, String messagePart) {
        RunResult result = run(source);
        assertFalse(result.success(), () -> "expected failure, got output: " + result.output());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.stage == Diagnostic.Stage.RUNTIME && d.message.contains(messagePart)),
            () -> "expected RUNTIME diagnostic containing '" + messagePart + "', got: " + result.diagnostics());
    }

    @Test
    @DisplayName("len returns string and array lengths")
    void lenReturnsStringAndArrayLengths() {
        String source = """
                print len("CodeFab");
                var values = Array(2);
                values[0] = "a";
                values[1] = "b";
                print len(values);
                """;

        assertEquals(List.of("7", "2"), output(source));
    }

    @Test
    @DisplayName("charAt returns one-character strings")
    void charAtReturnsOneCharacterStrings() {
        String source = """
                print charAt("CodeFab", 0);
                print charAt("CodeFab", 4);
                print charAt("CodeFab", 6);
                """;

        assertEquals(List.of("C", "F", "b"), output(source));
    }

    @Test
    @DisplayName("slice returns substring with exclusive end")
    void sliceReturnsSubstringWithExclusiveEnd() {
        String source = """
                print slice("CodeFab", 0, 4);
                print slice("CodeFab", 4, 7);
                print slice("CodeFab", 2, 2);
                """;

        assertEquals(List.of("Code", "Fab", ""), output(source));
    }

    @Test
    @DisplayName("push appends to arrays and returns the new length")
    void pushAppendsToArraysAndReturnsNewLength() {
        String source = """
                var tokens = Array(0);
                print push(tokens, "IDENTIFIER");
                print push(tokens, "EOF");
                print len(tokens);
                print tokens[0];
                print tokens[1];
                """;

        assertEquals(List.of("1", "2", "2", "IDENTIFIER", "EOF"), output(source));
    }

    @Test
    @DisplayName("native functions report stable runtime errors")
    void nativeFunctionsReportRuntimeErrors() {
        assertRuntimeError("print len(123);", "len() expects a string or array.");
        assertRuntimeError("print charAt(123, 0);", "charAt() expects a string.");
        assertRuntimeError("print charAt(\"a\", 1);", "String index out of bounds.");
        assertRuntimeError("print charAt(\"a\", 0.5);", "String index must be an integer.");
        assertRuntimeError("print slice(123, 0, 1);", "slice() expects a string.");
        assertRuntimeError("print slice(\"abc\", 2, 1);", "String slice out of bounds.");
        assertRuntimeError("print push(\"not array\", 1);", "push() expects an array.");
    }

    @Test
    @DisplayName("Array native function preserves existing behavior")
    void arrayNativeFunctionPreservesExistingBehavior() {
        assertEquals(List.of("[1, nil]"), output("var a = Array(2); a[0] = 1; print a;"));
        assertRuntimeError("var a = Array(\"two\");", "Array size must be a number.");
        assertRuntimeError("var a = Array(2.5);", "Array size must be an integer.");
        assertRuntimeError("var a = Array(-1);", "Array size must be non-negative.");
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew test --tests codefab.NativeFunctionTest
```

Expected:

- The test class compiles.
- Tests for `len`, `charAt`, `slice`, and `push` fail with runtime diagnostics such as `Undefined variable 'len'.`
- The `Array` preservation assertions may already pass or partially pass; that is acceptable because the RED reason is the missing bootstrap functions.

## Task 2: Add Callable Runtime Abstraction

**Files:**
- Create: `src/main/java/codefab/executor/CodeFabCallable.java`
- Create: `src/main/java/codefab/executor/NativeFunction.java`
- Modify: `src/main/java/codefab/executor/CodeFabFunction.java`
- Modify: `src/main/java/codefab/executor/Executor.java`

- [ ] **Step 1: Create `CodeFabCallable`**

Create `src/main/java/codefab/executor/CodeFabCallable.java`:

```java
package codefab.executor;

import codefab.core.Token;
import java.util.List;

interface CodeFabCallable {
    int arity();

    Object call(Executor executor, Token token, List<Object> arguments);
}
```

- [ ] **Step 2: Create `NativeFunction`**

Create `src/main/java/codefab/executor/NativeFunction.java`:

```java
package codefab.executor;

abstract class NativeFunction implements CodeFabCallable {
    private final String name;
    private final int arity;

    NativeFunction(String name, int arity) {
        this.name = name;
        this.arity = arity;
    }

    @Override
    public int arity() {
        return arity;
    }

    @Override
    public String toString() {
        return "<native fn " + name + ">";
    }
}
```

- [ ] **Step 3: Update `CodeFabFunction`**

Replace `CodeFabFunction` with:

```java
package codefab.executor;

import codefab.core.Stmt;
import codefab.core.Token;
import java.util.List;

/**
 * 런타임 함수 객체. 선언 시점의 Environment(클로저)를 캡처한다.
 */
class CodeFabFunction implements CodeFabCallable {
    final Stmt.FunctionStmt declaration;
    final Environment closure;

    CodeFabFunction(Stmt.FunctionStmt declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Executor executor, Token token, List<Object> arguments) {
        return executor.callUserFunction(this, token, arguments);
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
```

- [ ] **Step 4: Update `Executor` call dispatch**

In `src/main/java/codefab/executor/Executor.java`:

1. Delete the `ARRAY_BUILTIN` field.
2. In the constructor, register builtins:

```java
public Executor(OutputSink output, Environment globals) {
    this.output = output;
    this.environment = globals;
    defineNativeFunctions(globals);
}
```

3. Add `defineNativeFunctions` with at least `Array` first:

```java
private void defineNativeFunctions(Environment globals) {
    globals.define("Array", new NativeFunction("Array", 1) {
        @Override
        public Object call(Executor executor, Token token, List<Object> arguments) {
            Object sizeObj = arguments.get(0);
            if (!(sizeObj instanceof Double)) {
                throw new InterpreterRuntimeError(token, "Array size must be a number.");
            }
            int size = requireInteger(token, sizeObj, "Array size must be an integer.");
            if (size < 0) {
                throw new InterpreterRuntimeError(token, "Array size must be non-negative.");
            }
            return new ArrayList<>(Collections.nCopies(size, null));
        }
    });
}
```

4. Change `visitVariable` so `ARRAY` uses normal environment lookup by lexeme:

```java
@Override
public Object visitVariable(Expr.Variable expr) {
    if (expr.distance >= 0) {
        return environment.getAt(expr.distance, expr.name.lexeme);
    }
    return environment.get(expr.name);
}
```

5. Replace `visitCall` with generic callable dispatch:

```java
@Override
public Object visitCall(Expr.Call expr) {
    Object callee = evaluate(expr.callee);

    if (!(callee instanceof CodeFabCallable)) {
        throw new InterpreterRuntimeError(expr.paren, "Can only call functions.");
    }

    CodeFabCallable callable = (CodeFabCallable) callee;
    if (expr.arguments.size() != callable.arity()) {
        throw new InterpreterRuntimeError(expr.paren,
                "Expected " + callable.arity() + " arguments but got " + expr.arguments.size() + ".");
    }

    List<Object> args = new ArrayList<>();
    for (Expr arg : expr.arguments) {
        args.add(evaluate(arg));
    }

    return callable.call(this, expr.paren, args);
}
```

6. Add user-function call helper:

```java
Object callUserFunction(CodeFabFunction function, Token token, List<Object> args) {
    if (callDepth >= MAX_CALL_DEPTH) {
        throw new InterpreterRuntimeError(token,
                "Maximum call depth (" + MAX_CALL_DEPTH + ") exceeded.");
    }

    Environment callEnv = new Environment(function.closure);
    for (int i = 0; i < function.declaration.params.size(); i++) {
        callEnv.define(function.declaration.params.get(i).lexeme, args.get(i));
    }

    callDepth++;
    try {
        executeBlock(function.declaration.body, callEnv);
        return null;
    } catch (ReturnException ret) {
        return ret.value;
    } finally {
        callDepth--;
    }
}
```

7. Add integer helper:

```java
private static int requireInteger(Token token, Object value, String message) {
    if (!(value instanceof Double)) {
        throw new InterpreterRuntimeError(token, message);
    }
    double number = (Double) value;
    if (number != Math.floor(number) || Double.isInfinite(number) || Double.isNaN(number)) {
        throw new InterpreterRuntimeError(token, message);
    }
    return (int) number;
}
```

8. Update `stringify` by removing the `ARRAY_BUILTIN` branch.

- [ ] **Step 5: Run targeted tests**

Run:

```bash
./gradlew test --tests codefab.ExecutorTest --tests codefab.EndToEndTest
```

Expected:

- Existing function and array tests pass.
- If failures occur, they should be in call dispatch or `Array(n)` behavior; fix production code without changing tests.

## Task 3: Implement Bootstrap Native Functions

**Files:**
- Modify: `src/main/java/codefab/executor/Executor.java`

- [ ] **Step 1: Add native function registrations**

Extend `defineNativeFunctions` after `Array`:

```java
globals.define("len", new NativeFunction("len", 1) {
    @Override
    public Object call(Executor executor, Token token, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value instanceof String) {
            return (double) ((String) value).length();
        }
        if (value instanceof List) {
            return (double) ((List<?>) value).size();
        }
        throw new InterpreterRuntimeError(token, "len() expects a string or array.");
    }
});

globals.define("charAt", new NativeFunction("charAt", 2) {
    @Override
    public Object call(Executor executor, Token token, List<Object> arguments) {
        Object sourceObj = arguments.get(0);
        if (!(sourceObj instanceof String)) {
            throw new InterpreterRuntimeError(token, "charAt() expects a string.");
        }
        String source = (String) sourceObj;
        int index = requireInteger(token, arguments.get(1), "String index must be an integer.");
        if (index < 0 || index >= source.length()) {
            throw new InterpreterRuntimeError(token, "String index out of bounds.");
        }
        return Character.toString(source.charAt(index));
    }
});

globals.define("slice", new NativeFunction("slice", 3) {
    @Override
    public Object call(Executor executor, Token token, List<Object> arguments) {
        Object sourceObj = arguments.get(0);
        if (!(sourceObj instanceof String)) {
            throw new InterpreterRuntimeError(token, "slice() expects a string.");
        }
        String source = (String) sourceObj;
        int start = requireInteger(token, arguments.get(1), "String index must be an integer.");
        int end = requireInteger(token, arguments.get(2), "String index must be an integer.");
        if (start < 0 || end < start || end > source.length()) {
            throw new InterpreterRuntimeError(token, "String slice out of bounds.");
        }
        return source.substring(start, end);
    }
});

globals.define("push", new NativeFunction("push", 2) {
    @Override
    @SuppressWarnings("unchecked")
    public Object call(Executor executor, Token token, List<Object> arguments) {
        Object target = arguments.get(0);
        if (!(target instanceof List)) {
            throw new InterpreterRuntimeError(token, "push() expects an array.");
        }
        List<Object> list = (List<Object>) target;
        list.add(arguments.get(1));
        return (double) list.size();
    }
});
```

- [ ] **Step 2: Run Stage 1 targeted tests**

Run:

```bash
./gradlew test --tests codefab.NativeFunctionTest
```

Expected:

- All `NativeFunctionTest` tests pass.

- [ ] **Step 3: Run relevant regression tests**

Run:

```bash
./gradlew test --tests codefab.ExecutorTest --tests codefab.EndToEndTest --tests codefab.CodeFabSessionTest
```

Expected:

- Existing executor, end-to-end, and session tests pass.

## Task 4: Documentation and Progress Evidence

**Files:**
- Modify: `README.md`
- Modify: `_workspace/codefab-selfhost/progress.md`

- [ ] **Step 1: Update README native function docs**

In `README.md`, add a short subsection under "언어 문법" after 배열:

```markdown
### 내장 함수

Self-hosting과 일반 스크립트 편의를 위해 다음 내장 함수를 제공합니다.

| 함수 | 설명 |
|------|------|
| `len(value)` | 문자열 또는 배열의 길이를 반환 |
| `charAt(source, index)` | 문자열의 한 문자 반환 |
| `slice(source, start, end)` | 문자열의 `[start, end)` 구간 반환 |
| `push(array, value)` | 배열 끝에 값을 추가하고 새 길이를 반환 |

```
var text = "CodeFab";
print len(text);          // 7
print charAt(text, 4);    // F
print slice(text, 4, 7);  // Fab

var items = Array(0);
print push(items, "tok"); // 1
print items[0];           // tok
```
```

- [ ] **Step 2: Update progress ledger**

Update `_workspace/codefab-selfhost/progress.md`:

```markdown
| 0. Reconcile baseline | Complete | `_workspace/codefab-selfhost/01_contract_gap_audit.md` |
| 1. Bootstrap API | Complete | RED: `./gradlew test --tests codefab.NativeFunctionTest` failed because native functions were undefined. GREEN: `./gradlew test --tests codefab.NativeFunctionTest` passed. Full: `./gradlew test` passed. |
```

Use the exact observed command results. If a command result differs from the example wording, write the observed result instead.

- [ ] **Step 3: Run full verification**

Run:

```bash
./gradlew test
```

Expected:

- Build succeeds.
- All tests pass.

- [ ] **Step 4: Commit**

Only commit if the user has asked for commits in this session. Otherwise leave changes staged/unstaged and report exact verification results.

```bash
git add src/test/java/codefab/NativeFunctionTest.java \
  src/main/java/codefab/executor/CodeFabCallable.java \
  src/main/java/codefab/executor/NativeFunction.java \
  src/main/java/codefab/executor/CodeFabFunction.java \
  src/main/java/codefab/executor/Executor.java \
  README.md \
  _workspace/codefab-selfhost/progress.md
git commit -m "feat: add CodeFab bootstrap native functions"
```

## Self-Review

Spec coverage:

- `len(value)`: Task 1 tests string/array success and non-string/non-array error; Task 3 implements.
- `charAt(source, index)`: Task 1 tests success and type/range/integer errors; Task 3 implements.
- `slice(source, start, end)`: Task 1 tests success and type/range errors; Task 3 implements.
- `push(array, value)`: Task 1 tests append, length, retrieval, and non-array error; Task 3 implements.
- `chr(code)`, `ord(char)`, and `num(text)`: added during Stage 2 planning after discovering scanner-specific gaps around quotes, character ranges, and numeric literals; TDD evidence is recorded in `_workspace/codefab-selfhost/04_stage2_scanner_red_green.md`.
- Native callable abstraction: Task 2 implements and preserves `Array`.
- Full verification: Task 4 runs `./gradlew test`.

Placeholder scan:

- No implementation step uses placeholder markers.
- Progress ledger text gives a concrete evidence format and instructs the implementer to use observed command results.

Type consistency:

- `CodeFabCallable.call(Executor, Token, List<Object>)` is used consistently by `CodeFabFunction`, `NativeFunction`, and `Executor.visitCall`.
- Native helpers use `List` because current arrays are Java `List<Object>`.
