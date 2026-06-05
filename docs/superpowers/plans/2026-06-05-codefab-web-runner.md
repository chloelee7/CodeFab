# CodeFab Web Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local CodeFab web runner that serves a split editor/output UI and executes source through the existing interpreter over HTTP.

**Architecture:** Add a focused `codefab.web` package around Java 17 `HttpServer`. The web server owns HTTP, JSON, timeout, and static UI concerns; interpreter execution stays behind `CodeFab.run(String)`. The existing CLI gains a `web` subcommand without changing REPL, file-run, or debug behavior.

**Tech Stack:** Java 17, Gradle, JUnit 5, AssertJ, Java `HttpClient`, Java `HttpServer`, static HTML/CSS/JS.

---

## File Structure

- Create `src/main/java/codefab/web/CodeFabRunner.java`: small functional interface for executing source; tests can inject fake runners for timeout cases.
- Create `src/main/java/codefab/web/WebRunnerConfig.java`: immutable server defaults for port, request limit, and timeout.
- Create `src/main/java/codefab/web/WebJson.java`: minimal JSON request parsing and response serialization for the API contract.
- Create `src/main/java/codefab/web/WebRunnerServer.java`: owns `HttpServer`, endpoints, request validation, execution timeout, and static UI response.
- Create `src/main/java/codefab/web/WebOptions.java`: parses `factory web` CLI options.
- Modify `src/main/java/codefab/shell/Main.java`: dispatch `web` command and update usage text.
- Create `src/test/java/codefab/web/WebJsonTest.java`: RED/GREEN tests for JSON parsing/serialization.
- Create `src/test/java/codefab/web/WebRunnerServerTest.java`: RED/GREEN API, validation, timeout, and UI endpoint tests.
- Create `src/test/java/codefab/web/WebOptionsTest.java`: RED/GREEN CLI option parser tests.
- Modify `src/test/java/codefab/PromptShellTest.java` only if existing help/CLI behavior needs a focused regression; otherwise preserve existing tests untouched.

---

### Task 1: JSON Boundary

**Files:**
- Create: `src/test/java/codefab/web/WebJsonTest.java`
- Create: `src/main/java/codefab/web/WebJson.java`

- [ ] **Step 1: Write the failing JSON tests**

Create `src/test/java/codefab/web/WebJsonTest.java`:

```java
package codefab.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codefab.RunResult;
import codefab.core.Diagnostic;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebJsonTest {

    @Test
    void parsesSourceStringWithEscapes() {
        assertThat(WebJson.parseSource("{\"source\":\"print \\\"Hello\\\";\\n\"}"))
            .isEqualTo("print \"Hello\";\n");
    }

    @Test
    void rejectsMissingSource() {
        assertThatThrownBy(() -> WebJson.parseSource("{\"code\":\"print 1;\"}"))
            .isInstanceOf(WebJson.JsonException.class)
            .hasMessageContaining("source");
    }

    @Test
    void serializesRunResultWithStructuredDiagnostics() {
        RunResult result = new RunResult(
            false,
            List.of("partial"),
            List.of(new Diagnostic(Diagnostic.Stage.PARSER, 1, "Expect ';' after value.")));

        String json = WebJson.runResult(result);

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"output\":[\"partial\"]");
        assertThat(json).contains("\"stage\":\"PARSER\"");
        assertThat(json).contains("\"line\":1");
        assertThat(json).contains("\"message\":\"Expect ';' after value.\"");
        assertThat(json).contains("\"rendered\":\"[line 1] PARSER error: Expect ';' after value.\"");
    }

    @Test
    void serializesApiError() {
        assertThat(WebJson.error("Missing source."))
            .isEqualTo("{\"success\":false,\"error\":\"Missing source.\"}");
    }
}
```

- [ ] **Step 2: Run JSON tests to verify RED**

Run:

```bash
./gradlew test --tests codefab.web.WebJsonTest
```

Expected: compile failure because `codefab.web.WebJson` does not exist.

- [ ] **Step 3: Implement minimal JSON parser/serializer**

Create `src/main/java/codefab/web/WebJson.java` with package-private static helpers:

```java
package codefab.web;

import codefab.RunResult;
import codefab.core.Diagnostic;

final class WebJson {
    static final class JsonException extends RuntimeException {
        JsonException(String message) {
            super(message);
        }
    }

    private WebJson() {
    }

    static String parseSource(String json) {
        Parser parser = new Parser(json);
        return parser.parseSourceObject();
    }

    static String runResult(RunResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":").append(result.success());
        json.append(",\"output\":").append(stringArray(result.output()));
        json.append(",\"diagnostics\":[");
        for (int i = 0; i < result.diagnostics().size(); i++) {
            if (i > 0) json.append(',');
            Diagnostic diagnostic = result.diagnostics().get(i);
            json.append('{')
                .append("\"stage\":\"").append(diagnostic.stage).append("\",")
                .append("\"line\":").append(diagnostic.line).append(',')
                .append("\"message\":").append(quote(diagnostic.message)).append(',')
                .append("\"rendered\":").append(quote(diagnostic.render()))
                .append('}');
        }
        json.append("]}");
        return json.toString();
    }

    static String error(String message) {
        return "{\"success\":false,\"error\":" + quote(message) + "}";
    }

    private static String stringArray(Iterable<String> values) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) json.append(',');
            json.append(quote(value));
            first = false;
        }
        json.append(']');
        return json.toString();
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        quoted.append('"');
        return quoted.toString();
    }

    private static final class Parser {
        private final String input;
        private int current;

        Parser(String input) {
            this.input = input == null ? "" : input;
        }

        String parseSourceObject() {
            skipWhitespace();
            consume('{', "Expected JSON object.");
            String source = null;
            skipWhitespace();
            if (peek() != '}') {
                do {
                    String name = parseString();
                    skipWhitespace();
                    consume(':', "Expected ':' after property name.");
                    skipWhitespace();
                    if ("source".equals(name)) {
                        source = parseString();
                    } else {
                        skipValue();
                    }
                    skipWhitespace();
                } while (consumeIf(','));
            }
            consume('}', "Expected '}' after JSON object.");
            skipWhitespace();
            if (!isAtEnd()) throw new JsonException("Unexpected content after JSON object.");
            if (source == null) throw new JsonException("Missing source.");
            return source;
        }

        private void skipValue() {
            if (peek() == '"') {
                parseString();
                return;
            }
            while (!isAtEnd() && peek() != ',' && peek() != '}') {
                current++;
            }
        }

        private String parseString() {
            consume('"', "Expected JSON string.");
            StringBuilder value = new StringBuilder();
            while (!isAtEnd() && peek() != '"') {
                char c = advance();
                if (c == '\\') {
                    if (isAtEnd()) throw new JsonException("Unterminated escape sequence.");
                    char escaped = advance();
                    switch (escaped) {
                        case '"' -> value.append('"');
                        case '\\' -> value.append('\\');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        default -> throw new JsonException("Unsupported escape sequence.");
                    }
                } else {
                    value.append(c);
                }
            }
            consume('"', "Unterminated JSON string.");
            return value.toString();
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(peek())) current++;
        }

        private boolean consumeIf(char expected) {
            if (peek() != expected) return false;
            current++;
            return true;
        }

        private void consume(char expected, String message) {
            if (!consumeIf(expected)) throw new JsonException(message);
        }

        private char advance() {
            return input.charAt(current++);
        }

        private char peek() {
            return isAtEnd() ? '\0' : input.charAt(current);
        }

        private boolean isAtEnd() {
            return current >= input.length();
        }
    }
}
```

- [ ] **Step 4: Run JSON tests to verify GREEN**

Run:

```bash
./gradlew test --tests codefab.web.WebJsonTest
```

Expected: PASS.

- [ ] **Step 5: Commit JSON boundary**

```bash
git add src/main/java/codefab/web/WebJson.java src/test/java/codefab/web/WebJsonTest.java
git commit -m "feat: add web runner JSON boundary"
```

---

### Task 2: HTTP API Server

**Files:**
- Create: `src/test/java/codefab/web/WebRunnerServerTest.java`
- Create: `src/main/java/codefab/web/CodeFabRunner.java`
- Create: `src/main/java/codefab/web/WebRunnerConfig.java`
- Create: `src/main/java/codefab/web/WebRunnerServer.java`

- [ ] **Step 1: Write failing API tests**

Create `src/test/java/codefab/web/WebRunnerServerTest.java`:

```java
package codefab.web;

import static org.assertj.core.api.Assertions.assertThat;

import codefab.RunResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WebRunnerServerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private WebRunnerServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void runEndpointReturnsInterpreterOutput() throws Exception {
        server = startDefault();

        HttpResponse<String> response = post("/api/run", "{\"source\":\"print 1 + 2;\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"success\":true");
        assertThat(response.body()).contains("\"output\":[\"3\"]");
        assertThat(response.body()).contains("\"diagnostics\":[]");
    }

    @Test
    void runEndpointReturnsStructuredDiagnostics() throws Exception {
        server = startDefault();

        HttpResponse<String> response = post("/api/run", "{\"source\":\"print 1\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"success\":false");
        assertThat(response.body()).contains("\"stage\":\"PARSER\"");
        assertThat(response.body()).contains("Expect ';' after value.");
    }

    @Test
    void missingSourceReturnsClientErrorJson() throws Exception {
        server = startDefault();

        HttpResponse<String> response = post("/api/run", "{\"code\":\"print 1;\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("{\"success\":false,\"error\":\"Missing source.\"}");
    }

    @Test
    void oversizedRequestReturnsClientErrorJson() throws Exception {
        WebRunnerConfig config = new WebRunnerConfig(0, 10, 1_000);
        server = start(config, source -> new RunResult(true, List.of(), List.of()));

        HttpResponse<String> response = post("/api/run", "{\"source\":\"too large\"}");

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("Request body too large.");
    }

    @Test
    void slowRunnerReturnsTimeoutError() throws Exception {
        WebRunnerConfig config = new WebRunnerConfig(0, 65_536, 25);
        server = start(config, source -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new RunResult(true, List.of("late"), List.of());
        });

        HttpResponse<String> response = post("/api/run", "{\"source\":\"while (true) {}\"}");

        assertThat(response.statusCode()).isEqualTo(408);
        assertThat(response.body()).contains("Execution timed out.");
    }

    @Test
    void rootServesWorkbenchUi() throws Exception {
        server = startDefault();

        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueContaining("text/html");
        assertThat(response.body()).contains("CodeFab Runner");
        assertThat(response.body()).contains("<textarea");
        assertThat(response.body()).contains("/api/run");
    }

    private WebRunnerServer startDefault() throws IOException {
        return start(WebRunnerConfig.defaults(), source -> new codefab.CodeFab().run(source));
    }

    private WebRunnerServer start(WebRunnerConfig config, CodeFabRunner runner) throws IOException {
        WebRunnerServer started = new WebRunnerServer(new InetSocketAddress("127.0.0.1", 0), config, runner);
        started.start();
        return started;
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(server.uri().resolve(path))
            .timeout(Duration.ofSeconds(2))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        URI uri = server.uri().resolve(path);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
```

- [ ] **Step 2: Run API tests to verify RED**

Run:

```bash
./gradlew test --tests codefab.web.WebRunnerServerTest
```

Expected: compile failure because `WebRunnerServer`, `WebRunnerConfig`, and `CodeFabRunner` do not exist.

- [ ] **Step 3: Implement minimal web server**

Create:

```java
// src/main/java/codefab/web/CodeFabRunner.java
package codefab.web;

import codefab.RunResult;

@FunctionalInterface
public interface CodeFabRunner {
    RunResult run(String source);
}
```

```java
// src/main/java/codefab/web/WebRunnerConfig.java
package codefab.web;

public record WebRunnerConfig(int port, int maxBodyBytes, long timeoutMillis) {
    public static WebRunnerConfig defaults() {
        return new WebRunnerConfig(8080, 65_536, 1_000);
    }
}
```

Create `src/main/java/codefab/web/WebRunnerServer.java` implementing:

- `new WebRunnerServer(InetSocketAddress address, WebRunnerConfig config, CodeFabRunner runner)`
- `start()`, `stop()`, and `URI uri()`
- `GET /` returning inline HTML with A split workbench
- `POST /api/run` validating body size, parsing source, running with timeout, and returning JSON
- JSON API errors using `WebJson.error(message)`

- [ ] **Step 4: Run API tests to verify GREEN**

Run:

```bash
./gradlew test --tests codefab.web.WebRunnerServerTest
```

Expected: PASS.

- [ ] **Step 5: Commit HTTP API server**

```bash
git add src/main/java/codefab/web src/test/java/codefab/web/WebRunnerServerTest.java
git commit -m "feat: add local web runner server"
```

---

### Task 3: CLI Web Command

**Files:**
- Create: `src/test/java/codefab/web/WebOptionsTest.java`
- Create: `src/main/java/codefab/web/WebOptions.java`
- Modify: `src/main/java/codefab/shell/Main.java`

- [ ] **Step 1: Write failing CLI option tests**

Create `src/test/java/codefab/web/WebOptionsTest.java`:

```java
package codefab.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebOptionsTest {

    @Test
    void usesDefaultPortWhenNoPortIsProvided() {
        assertThat(WebOptions.parse(new String[] {"web"}).port()).isEqualTo(8080);
    }

    @Test
    void parsesExplicitPort() {
        assertThat(WebOptions.parse(new String[] {"web", "--port", "9090"}).port()).isEqualTo(9090);
    }

    @Test
    void rejectsMissingPortValue() {
        assertThatThrownBy(() -> WebOptions.parse(new String[] {"web", "--port"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--port requires a value");
    }

    @Test
    void rejectsInvalidPortValue() {
        assertThatThrownBy(() -> WebOptions.parse(new String[] {"web", "--port", "abc"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid port");
    }
}
```

- [ ] **Step 2: Run CLI option tests to verify RED**

Run:

```bash
./gradlew test --tests codefab.web.WebOptionsTest
```

Expected: compile failure because `WebOptions` does not exist.

- [ ] **Step 3: Implement CLI option parsing and Main dispatch**

Create `src/main/java/codefab/web/WebOptions.java`:

```java
package codefab.web;

public record WebOptions(int port) {
    public static WebOptions parse(String[] args) {
        int port = WebRunnerConfig.defaults().port();
        for (int i = 1; i < args.length; i++) {
            if ("--port".equals(args[i])) {
                if (i + 1 >= args.length) throw new IllegalArgumentException("--port requires a value.");
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid port: " + args[i]);
                }
                if (port < 1 || port > 65_535) throw new IllegalArgumentException("Invalid port: " + port);
            } else {
                throw new IllegalArgumentException("Unknown web option: " + args[i]);
            }
        }
        return new WebOptions(port);
    }
}
```

Modify `src/main/java/codefab/shell/Main.java`:

- import `codefab.web.WebOptions`, `codefab.web.WebRunnerConfig`, and `codefab.web.WebRunnerServer`
- before `run`/`debug` dispatch, handle `args[0].equals("web")`
- start `WebRunnerServer` on `127.0.0.1:<port>`
- print `CodeFab web runner: http://127.0.0.1:<actual-port>/`
- block the process using a wait object so the server remains alive
- update help text with `factory web [--port <port>]`

- [ ] **Step 4: Run CLI option tests and relevant existing tests**

Run:

```bash
./gradlew test --tests codefab.web.WebOptionsTest --tests codefab.PromptShellTest
```

Expected: PASS.

- [ ] **Step 5: Commit CLI command**

```bash
git add src/main/java/codefab/shell/Main.java src/main/java/codefab/web/WebOptions.java src/test/java/codefab/web/WebOptionsTest.java
git commit -m "feat: add web runner CLI command"
```

---

### Task 4: Full Verification And Manual Smoke

**Files:**
- Modify: only if verification exposes a real defect.

- [ ] **Step 1: Run full test suite**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 2: Run build**

Run:

```bash
./gradlew build
```

Expected: PASS.

- [ ] **Step 3: Start local server for smoke verification**

Run:

```bash
./gradlew run --args="web --port 18080"
```

Expected stdout contains:

```text
CodeFab web runner: http://127.0.0.1:18080/
```

- [ ] **Step 4: Smoke the API from another shell**

Run:

```bash
curl -sS -X POST http://127.0.0.1:18080/api/run \
  -H 'Content-Type: application/json' \
  -d '{"source":"print 2 + 3;"}'
```

Expected response contains:

```json
"success":true
"output":["5"]
```

Run:

```bash
curl -sS -X POST http://127.0.0.1:18080/api/run \
  -H 'Content-Type: application/json' \
  -d '{"source":"print 2"}'
```

Expected response contains:

```json
"success":false
"stage":"PARSER"
```

- [ ] **Step 5: Browser smoke**

Open `http://127.0.0.1:18080/`, verify:

- A split editor/output UI appears.
- Run button executes the initial source.
- `Ctrl+Enter` or `Cmd+Enter` runs source.
- Syntax errors appear in diagnostics.

- [ ] **Step 6: Commit final verification fixes if needed**

Only commit if fixes were made during full verification:

```bash
git add <changed-files>
git commit -m "fix: polish web runner verification issues"
```

---

## Self-Review

- Spec coverage: CLI command, API endpoint, JSON response, diagnostics, timeout, request limit, A split UI, and verification are all covered by tasks.
- Placeholder scan: no unresolved placeholder or unspecified test steps remain.
- Type consistency: `WebRunnerConfig`, `WebRunnerServer`, `CodeFabRunner`, `WebJson`, and `WebOptions` names are consistent across tasks.
