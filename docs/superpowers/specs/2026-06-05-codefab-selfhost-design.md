# CodeFab Selfhost Design

## Goal

Build toward a full self-hosted CodeFab interpreter: CodeFab code that can scan, parse, check, and execute CodeFab programs, eventually matching the current Java interpreter's language behavior and user-facing run path.

This design intentionally preserves the full target. Early subset milestones are validation gates, not a reduced definition of success.

## Current Baseline

The Java interpreter is organized as Scanner -> Parser -> Checker -> ConstantFolder -> Executor -> Shell. Current CodeFab language support includes variables, blocks, `if`, `while`, `for`, functions, return, recursion, arrays, strings, numbers, booleans, and `nil`.

The language is still missing practical primitives for writing a scanner and parser in CodeFab: string length, character access, slicing, dynamic array append, and array length. These are the first bootstrap blockers.

The shared contract is also ahead of current source in some places, especially around `CheckResult`, locals maps, and wrapper types for callable/array values. The selfhost path starts with a reconciliation audit so the CodeFab implementation follows an explicit authority instead of stale documentation.

## Architecture

Use a staged pipeline harness:

1. Baseline reconciliation.
2. Java-hosted bootstrap API.
3. CodeFab-written scanner.
4. CodeFab-written parser.
5. CodeFab-written executor for a subset.
6. Full semantics and diagnostics parity.
7. CLI integration.

Each stage has its own artifact and exit gate under `_workspace/codefab-selfhost/`. Java host changes use the existing CodeFab TDD workflow. Selfhost CodeFab changes use golden fixtures and Java-vs-selfhost parity checks.

## Data Representation

Until CodeFab has richer records, selfhost structures use plain arrays:

- Token: `[type, lexeme, literal, line]`
- AST node: `[kind, field1, field2, ...]`
- Diagnostic: `[stage, line, message]`
- Environment: association-list arrays, upgraded only if fixtures prove the need

This keeps the first selfhost implementation expressible in the current language plus a small bootstrap standard library.

## Bootstrap API

Stage 1 should add only the primitives needed to write scanner/parser code:

- `len(value)` for strings and arrays
- `charAt(source, index)` for one-character string access
- `slice(source, start, end)` for substring extraction
- `push(array, value)` for dynamic token/AST construction
- `chr(code)` and `ord(char)` for scanner character handling
- `num(text)` for numeric literal conversion

Any broader standard-library work should be split out and justified by selfhost fixtures.

## Testing

For Java host changes:

- Add or update a failing JUnit test first.
- Run the targeted test and record the RED reason.
- Implement the smallest passing change.
- Run the targeted test and `./gradlew test` when Java behavior changed.

For selfhost stages:

- Scanner fixtures compare CodeFab token records with Java scanner expectations.
- Parser fixtures compare AST record shape.
- Executor fixtures compare output and diagnostics from Java runner and selfhost runner.
- CLI fixtures prove the final user-facing selfhost invocation.

## Acceptance

The full goal is complete only when:

- `selfhost/*.cfab` implements scanner, parser, checker, executor, and runner behavior.
- A user-facing selfhost run command or documented equivalent exists.
- Representative current Java tests or fixture equivalents pass through both Java and selfhost paths.
- Diagnostic stage, line, and message behavior is verified for the accepted language contract.
- Final verification commands are recorded in `_workspace/codefab-selfhost/final/summary.md`.
