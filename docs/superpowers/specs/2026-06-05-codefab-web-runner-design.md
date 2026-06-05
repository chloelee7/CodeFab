# CodeFab Web Runner MVP Design

## Goal

Create a local web runner for CodeFab that behaves like a minimal online compiler: edit CodeFab source in a browser, click Run, and see output plus diagnostics.

The first version is a local MVP, not a public multi-user service.

## Approved Choices

- Runtime approach: Java 17 built-in `com.sun.net.httpserver.HttpServer`.
- Frontend approach: static HTML, CSS, and JavaScript.
- First-screen layout: split workbench with editor on the left and output/diagnostics on the right.
- No Vue, React, Spring Boot, accounts, saved files, sharing links, or public-hosting hardening in this pass.

## CLI Contract

Add a `web` command to the existing `factory` CLI.

```bash
./gradlew run --args="web"
./gradlew run --args="web --port 8081"
```

Behavior:

- `factory web` starts the local server on a default port.
- `--port <number>` overrides the port.
- Startup prints the local URL.
- Existing REPL, `run`, `debug`, and help behavior remains unchanged.

## Server Contract

Add a small web package under `src/main/java/codefab/web`.

Endpoints:

- `GET /`: serve the web UI.
- `GET /assets/...`: serve static CSS/JS if split into separate files.
- `POST /api/run`: execute one source string and return JSON.

Request:

```json
{ "source": "print \"Hello\";" }
```

Response:

```json
{
  "success": true,
  "output": ["Hello"],
  "diagnostics": []
}
```

Diagnostics include structured fields:

```json
{
  "stage": "PARSER",
  "line": 1,
  "message": "Expected ';' after value.",
  "rendered": "[line 1] PARSER error: Expected ';' after value."
}
```

## Execution Model

Each `/api/run` request uses a new `CodeFab` execution, which creates a fresh `CodeFabSession`. That keeps browser runs isolated from each other and avoids REPL-style global state persistence.

The API should keep the interpreter boundary simple:

1. Parse JSON request.
2. Validate source is present and within size limit.
3. Run `new CodeFab().run(source)` with a timeout wrapper.
4. Serialize `RunResult`.

## MVP Safety Limits

This is local-only, but it still needs basic bounds:

- Reject request bodies larger than a small fixed limit.
- Reject missing or non-string `source`.
- Run execution through a timeout so infinite loops do not hang the HTTP handler forever.
- Return API errors as JSON with an HTTP status code.

The timeout is a local MVP guard, not a sandbox. Public deployment would need stronger process isolation and rate limiting.

## UI Contract

The first UI is a work-focused split layout:

- Top bar: CodeFab Runner title, Run button, optional status text.
- Left pane: code editor implemented as a large monospace `textarea`.
- Right pane: Output and Diagnostics panels.
- Keyboard shortcut: `Ctrl+Enter` or `Cmd+Enter` runs the code.
- Initial source: a small valid CodeFab example.

States:

- Idle: editor editable, output/diagnostics show last result or placeholders.
- Running: Run button disabled, status shows running.
- Success: output lines shown, diagnostics empty.
- Failure: output lines shown if any, diagnostics rendered clearly.
- API error: show a concise error in diagnostics.

## Testing Strategy

Use TDD for behavior changes.

Focused tests:

- `POST /api/run` returns output for valid source.
- `POST /api/run` returns structured diagnostics for invalid source.
- Missing source returns a JSON client error.
- Oversized request returns a JSON client error.
- Existing `run`, `debug`, help, and no-arg REPL routing remain intact where practical.

Verification:

```bash
./gradlew test
./gradlew build
```

Manual verification:

```bash
./gradlew run --args="web"
```

Then open the printed URL, run a valid example, and run a syntax-error example.

## Out Of Scope

- User accounts.
- Saving code snippets.
- Shareable links.
- Public hosting hardening.
- WebSocket REPL sessions.
- Multi-file projects.
- Syntax highlighting beyond basic textarea styling.
