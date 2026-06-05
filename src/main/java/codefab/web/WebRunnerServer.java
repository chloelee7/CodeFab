package codefab.web;

import codefab.RunResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class WebRunnerServer {
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String HTML_CONTENT_TYPE = "text/html; charset=utf-8";

    private final HttpServer server;
    private final WebRunnerConfig config;
    private final CodeFabRunner runner;
    private final ExecutorService httpExecutor;
    private final ExecutorService runExecutor;

    public WebRunnerServer(
        InetSocketAddress address,
        WebRunnerConfig config,
        CodeFabRunner runner
    ) throws IOException {
        this.server = HttpServer.create(address, 0);
        this.config = config;
        this.runner = runner;
        this.httpExecutor = Executors.newCachedThreadPool(namedDaemonFactory("codefab-web-http"));
        this.runExecutor = Executors.newCachedThreadPool(namedDaemonFactory("codefab-web-run"));

        this.server.createContext("/", this::handleRoot);
        this.server.createContext("/api/run", this::handleRun);
        this.server.setExecutor(httpExecutor);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        runExecutor.shutdownNow();
        httpExecutor.shutdownNow();
    }

    public URI uri() {
        InetSocketAddress address = server.getAddress();
        return URI.create("http://" + address.getHostString() + ":" + address.getPort() + "/");
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, WebJson.error("Method not allowed."));
            return;
        }
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            sendJson(exchange, 404, WebJson.error("Not found."));
            return;
        }
        send(exchange, 200, HTML_CONTENT_TYPE, indexHtml());
    }

    private void handleRun(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, WebJson.error("Method not allowed."));
            return;
        }

        String requestBody;
        try {
            requestBody = readBody(exchange.getRequestBody(), config.maxBodyBytes());
        } catch (BodyTooLargeException e) {
            sendJson(exchange, 413, WebJson.error("Request body too large."));
            return;
        }

        String source;
        try {
            source = WebJson.parseSource(requestBody);
        } catch (WebJson.JsonException e) {
            sendJson(exchange, 400, WebJson.error(e.getMessage()));
            return;
        }

        Future<RunResult> future = runExecutor.submit(runTask(source));
        try {
            RunResult result = future.get(config.timeoutMillis(), TimeUnit.MILLISECONDS);
            sendJson(exchange, 200, WebJson.runResult(result));
        } catch (TimeoutException e) {
            future.cancel(true);
            sendJson(exchange, 408, WebJson.error("Execution timed out."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            sendJson(exchange, 500, WebJson.error("Execution interrupted."));
        } catch (ExecutionException | CancellationException e) {
            sendJson(exchange, 500, WebJson.error("Execution failed."));
        }
    }

    private Callable<RunResult> runTask(String source) {
        return () -> runner.run(source);
    }

    private static String readBody(InputStream input, int maxBytes) throws IOException {
        byte[] buffer = new byte[1024];
        int total = 0;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new BodyTooLargeException();
            }
            body.write(buffer, 0, read);
        }
        return body.toString(StandardCharsets.UTF_8);
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        send(exchange, statusCode, JSON_CONTENT_TYPE, json);
    }

    private void send(HttpExchange exchange, int statusCode, String contentType, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        return new ThreadFactory() {
            private int nextId = 1;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, prefix + "-" + nextId++);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    private static String indexHtml() {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>CodeFab Runner</title>
              <style>
                :root {
                  color-scheme: light;
                  --bg: #f5f7fb;
                  --panel: #ffffff;
                  --text: #1f2937;
                  --muted: #667085;
                  --line: #d6dbe6;
                  --accent: #146c5c;
                  --accent-strong: #0f574a;
                  --editor: #171923;
                  --editor-text: #e8eefc;
                  --danger: #b42318;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  min-height: 100vh;
                  background: var(--bg);
                  color: var(--text);
                  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                }
                .app {
                  min-height: 100vh;
                  display: grid;
                  grid-template-rows: 56px 1fr;
                }
                header {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 16px;
                  padding: 0 20px;
                  background: var(--panel);
                  border-bottom: 1px solid var(--line);
                }
                h1 {
                  margin: 0;
                  font-size: 18px;
                  font-weight: 700;
                }
                .actions {
                  display: flex;
                  align-items: center;
                  gap: 12px;
                }
                #status {
                  color: var(--muted);
                  font-size: 13px;
                  min-width: 84px;
                  text-align: right;
                }
                button {
                  border: 0;
                  border-radius: 6px;
                  padding: 9px 14px;
                  background: var(--accent);
                  color: #ffffff;
                  font-weight: 700;
                  cursor: pointer;
                }
                button:hover { background: var(--accent-strong); }
                button:disabled {
                  cursor: not-allowed;
                  opacity: .65;
                }
                main {
                  min-height: 0;
                  display: grid;
                  grid-template-columns: minmax(0, 1.15fr) minmax(320px, .85fr);
                }
                .editor-pane,
                .result-pane {
                  min-height: 0;
                  padding: 18px;
                }
                .editor-pane {
                  border-right: 1px solid var(--line);
                }
                .pane-title {
                  display: flex;
                  justify-content: space-between;
                  align-items: baseline;
                  margin-bottom: 10px;
                  color: var(--muted);
                  font-size: 13px;
                  font-weight: 700;
                }
                .shortcut {
                  font-weight: 500;
                }
                textarea {
                  width: 100%;
                  height: calc(100vh - 110px);
                  min-height: 420px;
                  resize: none;
                  border: 1px solid #111827;
                  border-radius: 8px;
                  padding: 16px;
                  background: var(--editor);
                  color: var(--editor-text);
                  font: 14px/1.55 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                  outline: none;
                  tab-size: 2;
                }
                textarea:focus {
                  box-shadow: 0 0 0 3px rgba(20, 108, 92, .22);
                }
                .result-pane {
                  display: grid;
                  grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
                  gap: 18px;
                }
                section {
                  min-height: 0;
                  display: grid;
                  grid-template-rows: auto 1fr;
                }
                pre {
                  margin: 0;
                  min-height: 0;
                  overflow: auto;
                  border: 1px solid var(--line);
                  border-radius: 8px;
                  padding: 14px;
                  background: var(--panel);
                  color: var(--text);
                  font: 13px/1.5 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                  white-space: pre-wrap;
                }
                #diagnostics.has-error {
                  border-color: rgba(180, 35, 24, .35);
                  color: var(--danger);
                }
                @media (max-width: 820px) {
                  .app { grid-template-rows: auto 1fr; }
                  header {
                    align-items: flex-start;
                    flex-direction: column;
                    padding: 14px;
                  }
                  .actions {
                    width: 100%;
                    justify-content: space-between;
                  }
                  main {
                    grid-template-columns: 1fr;
                  }
                  .editor-pane {
                    border-right: 0;
                    border-bottom: 1px solid var(--line);
                  }
                  textarea {
                    height: 48vh;
                    min-height: 300px;
                  }
                  .result-pane {
                    grid-template-rows: 240px 240px;
                  }
                }
              </style>
            </head>
            <body>
              <div class="app">
                <header>
                  <h1>CodeFab Runner</h1>
                  <div class="actions">
                    <span id="status">Ready</span>
                    <button id="run" type="button">Run</button>
                  </div>
                </header>
                <main>
                  <div class="editor-pane">
                    <div class="pane-title">
                      <span>main.cfab</span>
                      <span class="shortcut">Ctrl/Command + Enter</span>
                    </div>
                    <textarea id="source" spellcheck="false">var total = 0;
            for (var i = 1; i &lt;= 5; i = i + 1) {
              total = total + i;
            }
            print total;</textarea>
                  </div>
                  <div class="result-pane">
                    <section>
                      <div class="pane-title"><span>Output</span></div>
                      <pre id="output">No output yet.</pre>
                    </section>
                    <section>
                      <div class="pane-title"><span>Diagnostics</span></div>
                      <pre id="diagnostics">No errors.</pre>
                    </section>
                  </div>
                </main>
              </div>
              <script>
                const source = document.getElementById('source');
                const runButton = document.getElementById('run');
                const statusText = document.getElementById('status');
                const output = document.getElementById('output');
                const diagnostics = document.getElementById('diagnostics');

                async function runCode() {
                  runButton.disabled = true;
                  statusText.textContent = 'Running';
                  diagnostics.classList.remove('has-error');
                  try {
                    const response = await fetch('/api/run', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ source: source.value })
                    });
                    const payload = await response.json();
                    output.textContent = payload.output && payload.output.length
                      ? payload.output.join('\\n')
                      : 'No output.';
                    if (payload.error) {
                      diagnostics.textContent = payload.error;
                      diagnostics.classList.add('has-error');
                      statusText.textContent = 'Error';
                    } else if (payload.diagnostics && payload.diagnostics.length) {
                      diagnostics.textContent = payload.diagnostics.map(d => d.rendered).join('\\n');
                      diagnostics.classList.add('has-error');
                      statusText.textContent = 'Failed';
                    } else {
                      diagnostics.textContent = 'No errors.';
                      statusText.textContent = 'Done';
                    }
                  } catch (error) {
                    output.textContent = 'No output.';
                    diagnostics.textContent = error.message;
                    diagnostics.classList.add('has-error');
                    statusText.textContent = 'Error';
                  } finally {
                    runButton.disabled = false;
                  }
                }

                runButton.addEventListener('click', runCode);
                source.addEventListener('keydown', event => {
                  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                    event.preventDefault();
                    runCode();
                  }
                });
              </script>
            </body>
            </html>
            """;
    }

    private static final class BodyTooLargeException extends IOException {
    }
}
