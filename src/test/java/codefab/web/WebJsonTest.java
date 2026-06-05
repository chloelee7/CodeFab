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
