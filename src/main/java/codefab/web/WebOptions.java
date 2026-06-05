package codefab.web;

public record WebOptions(int port) {

    public static WebOptions parse(String[] args) {
        int port = WebRunnerConfig.defaults().port();
        for (int i = 1; i < args.length; i++) {
            if ("--port".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--port requires a value.");
                }
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid port: " + args[i]);
                }
                if (port < 1 || port > 65_535) {
                    throw new IllegalArgumentException("Invalid port: " + port);
                }
            } else {
                throw new IllegalArgumentException("Unknown web option: " + args[i]);
            }
        }
        return new WebOptions(port);
    }
}
