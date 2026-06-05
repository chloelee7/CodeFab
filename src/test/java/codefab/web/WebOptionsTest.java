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
