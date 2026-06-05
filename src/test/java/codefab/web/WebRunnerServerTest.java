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
        if (server != null) {
            server.stop();
        }
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
        assertThat(response.headers().firstValue("Content-Type"))
            .hasValueSatisfying(value -> assertThat(value).contains("text/html"));
        assertThat(response.body()).contains("CodeFab Runner");
        assertThat(response.body()).contains("<textarea");
        assertThat(response.body()).contains("/api/run");
    }

    private WebRunnerServer startDefault() throws IOException {
        return start(WebRunnerConfig.defaults(), source -> new codefab.CodeFab().run(source));
    }

    private WebRunnerServer start(WebRunnerConfig config, CodeFabRunner runner) throws IOException {
        WebRunnerServer started = new WebRunnerServer(
            new InetSocketAddress("127.0.0.1", 0),
            config,
            runner);
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
