package codefab.web;

public record WebRunnerConfig(int port, int maxBodyBytes, long timeoutMillis) {

    public static WebRunnerConfig defaults() {
        return new WebRunnerConfig(8080, 65_536, 1_000);
    }
}
