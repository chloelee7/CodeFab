# CodeFab Interpreter

A small tree-walking interpreter for a Lox-like scripting language, written in
Java 17. Source goes through a three-stage pipeline:

```
source ─▶ Assembler (Scanner ▸ Parser) ─▶ Checker ─▶ Executor ─▶ output
            syntax errors                  static errors  runtime errors
```

Each stage owns exactly one class of error and nothing else. The host language's
`eval`, `ScriptEngine`, `JShell`, and external processes are **not** used — the
scanner, parser, checker, and executor are all hand-written.

## Architecture

| Package              | Responsibility                                                        |
|----------------------|-----------------------------------------------------------------------|
| `codefab.core`       | Shared data: `Token`, `TokenType`, `Expr`/`Stmt` AST, `Diagnostic`, `OutputSink`. |
| `codefab.assembler`  | `Scanner` (text → tokens) and `Parser` (tokens → AST), plus syntax diagnostics. |
| `codefab.checker`    | `Checker` — DFS static analysis (duplicate declarations, self-init reads). |
| `codefab.executor`   | `Environment` (scoped variable store) and `Executor` (DFS evaluation).|
| `codefab.shell`      | `PromptShell` REPL and `Main` CLI entry point.                        |
| `codefab` (facade)   | `CodeFab`, `CodeFabSession`, `RunResult`, `CollectingOutputSink`.     |

### Design rules honored

- An `Expr` never contains a `Stmt`. `Expr` holds only `Expr` and `Token` fields;
  `Stmt` may hold `Expr`, `Stmt`, and `Token` fields. Tokens are node fields, not nodes.
- Parser, checker, and executor are separate units. No parsing in the executor,
  no execution in the checker.
- Core execution never touches `System.out`; output goes through an injected
  `OutputSink`, so tests capture printed lines directly.
- Diagnostics carry a stage and (when known) a line number.

## Language

### Grammar

```
program        -> declaration* EOF ;
declaration    -> varDecl | statement ;
varDecl        -> "var" IDENTIFIER ( "=" expression )? ";" ;
statement      -> printStmt | ifStmt | forStmt | block | exprStmt ;
printStmt      -> "print" expression ";" ;
ifStmt         -> "if" "(" expression ")" statement ( "else" statement )? ;
forStmt        -> "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
block          -> "{" declaration* "}" ;
exprStmt       -> expression ";" ;
expression     -> assignment ;
assignment     -> IDENTIFIER "=" assignment | logic_or ;
logic_or       -> logic_and ( "or" logic_and )* ;
logic_and      -> equality ( "and" equality )* ;
equality       -> comparison ( ( "==" | "!=" ) comparison )* ;
comparison     -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term           -> factor ( ( "-" | "+" ) factor )* ;
factor         -> unary ( ( "/" | "*" ) unary )* ;
unary          -> ( "!" | "-" | "+" ) unary | primary ;
primary        -> NUMBER | STRING | "true" | "false" | IDENTIFIER | "(" expression ")" ;
```

### Semantics

- **Values**: number (`double`), string, boolean, nil.
- **Truthiness**: `false` and nil are falsey; everything else is truthy.
- **`+`** adds two numbers or concatenates two strings. Mixing types is a runtime
  error: `Operands must be two numbers or two strings.`
- **`-` `*` `/`** and comparisons require numbers. Division by zero is a runtime error.
- **Printing** drops the trailing `.0` from integral numbers: `5` and `5.0` both print `5`,
  while `3.14` prints `3.14`. Strings print without quotes; booleans print `true`/`false`.
- **Scopes**: a global scope plus a fresh scope per block. Variable lookup walks
  outward, so inner scopes can read and assign outer variables and shadow them locally.
  A `for` loop has its own scope, so its initializer variable does not leak.
- **Checker rules**: declaring the same name twice in one scope is an error
  (`Already a variable with this name in this scope.`), and reading a variable inside
  its own initializer is an error (`Can't read local variable in initializer.`).
  If the checker reports anything, the executor does not run.

### Examples

```
print 1 + 2 * 3;                 // 7
print (1 + 2) * 3;               // 9
print "Hello, " + "CodeFab!";    // Hello, CodeFab!
print 5.0;                       // 5

var a = 10;
var b = 20;
print a + b;                     // 30

var x = "global";
{ var x = "inner"; print x; }    // inner
print x;                         // global

if (false) print "no"; else print "kfc";   // kfc

for (var j = 0; j < 3; j = j + 1) print j;  // 0 1 2
```

## Running

The project uses Gradle (wrapper included) and JUnit 5.

```bash
# Run the full test suite
./gradlew test

# Build a runnable distribution
./gradlew installDist

# Run a script file
./build/install/codefab/bin/codefab examples/demo.cf

# Start the interactive REPL (multi-line blocks supported; :exit / :quit to leave)
./build/install/codefab/bin/codefab

# Usage help
./build/install/codefab/bin/codefab --help
```

### REPL

The REPL keeps variables alive between inputs and accumulates a multi-line buffer
until parentheses and braces are balanced and the input ends in `;` or `}`:

```
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

## Testing

```bash
./gradlew test
```

Tests are organized as scanner/parser/checker unit tests plus end-to-end tests
(`NormalOperationTest`, `ErrorTest`, `SessionTest`, `PromptShellTest`) that drive
the full pipeline through the `CodeFab` facade and assert on captured output and
diagnostics — never on `System.out`.
