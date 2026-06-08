package codefab;

import codefab.core.Diagnostic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SelfHostCodeFab {
    private static final String RESULT_MARKER = "__CODEFAB_SELFHOST_RESULT_V1__";
    private static final int SELFHOST_HOST_MAX_CALL_DEPTH = 50_000;
    private static final long SELFHOST_HOST_STACK_BYTES = 128L * 1024L * 1024L;

    private final List<Path> bootstrapPaths;

    public SelfHostCodeFab() {
        this(List.of(
            Path.of("selfhost/scanner.cfab"),
            Path.of("selfhost/parser.cfab"),
            Path.of("selfhost/checker.cfab"),
            Path.of("selfhost/executor.cfab"),
            Path.of("selfhost/runner.cfab")));
    }

    SelfHostCodeFab(List<Path> bootstrapPaths) {
        this.bootstrapPaths = List.copyOf(bootstrapPaths);
    }

    public RunResult run(String source) {
        String bootstrap;
        try {
            bootstrap = readBootstrap();
        } catch (IOException e) {
            Diagnostic diagnostic = new Diagnostic(
                Diagnostic.Stage.RUNTIME, 0, "Unable to load selfhost bootstrap: " + e.getMessage());
            return new RunResult(false, List.of(), List.of(diagnostic));
        }

        RunResult hostResult = runHostProgram(buildHostProgram(bootstrap, source));
        if (!hostResult.success()) {
            return hostResult;
        }
        try {
            return parseSelfHostResult(hostResult.output());
        } catch (IllegalArgumentException | IndexOutOfBoundsException error) {
            Diagnostic diagnostic = malformedResultDiagnostic();
            return new RunResult(false, hostResult.output(), List.of(diagnostic));
        }
    }

    private RunResult runHostProgram(String hostProgram) {
        RunResult[] result = new RunResult[1];
        Throwable[] failure = new Throwable[1];
        Thread thread = new Thread(null, () -> {
            try {
                result[0] = new CodeFabSession(SELFHOST_HOST_MAX_CALL_DEPTH).run(hostProgram);
            } catch (Throwable throwable) {
                failure[0] = throwable;
            }
        }, "codefab-selfhost-host", SELFHOST_HOST_STACK_BYTES);

        thread.start();
        try {
            thread.join();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            Diagnostic diagnostic = new Diagnostic(
                Diagnostic.Stage.RUNTIME, 0, "Selfhost runner interrupted.");
            return new RunResult(false, List.of(), List.of(diagnostic));
        }

        if (failure[0] != null) {
            Diagnostic diagnostic = new Diagnostic(
                Diagnostic.Stage.RUNTIME, 0,
                "Selfhost host execution failed: " + failure[0].getClass().getSimpleName());
            return new RunResult(false, List.of(), List.of(diagnostic));
        }

        return result[0];
    }

    private String readBootstrap() throws IOException {
        StringBuilder source = new StringBuilder();
        for (Path path : bootstrapPaths) {
            source.append(readBootstrapFile(path)).append('\n');
        }
        return source.toString();
    }

    private String readBootstrapFile(Path path) throws IOException {
        if (Files.exists(path)) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }

        String resourceName = path.toString().replace('\\', '/');
        InputStream stream = SelfHostCodeFab.class.getClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IOException("missing " + path);
        }

        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String buildHostProgram(String bootstrap, String source) {
        return bootstrap
            + "var result = run(" + codeFabStringExpression(source) + ");\n"
            + "print " + quote(RESULT_MARKER) + ";\n"
            + "print result[0];\n"
            + "var output = result[1];\n"
            + "print len(output);\n"
            + "for (var i = 0; i < len(output); i = i + 1) {\n"
            + "  print output[i];\n"
            + "}\n"
            + "var diagnostics = result[2];\n"
            + "print len(diagnostics);\n"
            + "for (var j = 0; j < len(diagnostics); j = j + 1) {\n"
            + "  print diagnostics[j];\n"
            + "}\n";
    }

    private RunResult parseSelfHostResult(List<String> lines) {
        int marker = lines.indexOf(RESULT_MARKER);
        if (marker < 0) {
            return new RunResult(false, lines, List.of(malformedResultDiagnostic()));
        }

        int cursor = marker + 1;
        boolean success = Boolean.parseBoolean(lines.get(cursor++));

        int outputCount = parseCount(lines.get(cursor++));
        List<String> output = new ArrayList<>();
        for (int i = 0; i < outputCount; i++) {
            output.add(lines.get(cursor++));
        }

        int diagnosticCount = parseCount(lines.get(cursor++));
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int i = 0; i < diagnosticCount; i++) {
            diagnostics.add(parseDiagnostic(lines.get(cursor++)));
        }

        return new RunResult(success, output, diagnostics);
    }

    private static Diagnostic malformedResultDiagnostic() {
        return new Diagnostic(Diagnostic.Stage.RUNTIME, 0, "Selfhost runner emitted malformed result.");
    }

    private static int parseCount(String text) {
        return (int) Double.parseDouble(text);
    }

    private static Diagnostic parseDiagnostic(String text) {
        if (text.startsWith("[") && text.endsWith("]")) {
            String body = text.substring(1, text.length() - 1);
            int firstComma = body.indexOf(',');
            if (firstComma > 0) {
                String stageText = body.substring(0, firstComma).trim();
                String rest = body.substring(firstComma + 1).trim();
                int line = 0;
                String message = rest;
                int secondComma = rest.indexOf(',');
                if (secondComma > 0) {
                    String possibleLine = rest.substring(0, secondComma).trim();
                    try {
                        line = parseCount(possibleLine);
                        message = rest.substring(secondComma + 1).trim();
                    } catch (NumberFormatException ignored) {
                        line = 0;
                        message = rest;
                    }
                }
                try {
                    return new Diagnostic(Diagnostic.Stage.valueOf(stageText), line, message);
                } catch (IllegalArgumentException ignored) {
                    return new Diagnostic(Diagnostic.Stage.RUNTIME, 0, text);
                }
            }
        }
        return new Diagnostic(Diagnostic.Stage.RUNTIME, 0, text);
    }

    private static String codeFabStringExpression(String value) {
        List<String> terms = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\n' || c == '\r') {
                flushLiteralTerm(terms, literal);
                terms.add("chr(" + (int) c + ")");
            } else {
                literal.append(c);
            }
        }
        flushLiteralTerm(terms, literal);
        if (terms.isEmpty()) {
            return quote("");
        }
        return String.join(" + ", terms);
    }

    private static void flushLiteralTerm(List<String> terms, StringBuilder literal) {
        if (literal.length() == 0) {
            return;
        }
        terms.add(quote(literal.toString()));
        literal.setLength(0);
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
