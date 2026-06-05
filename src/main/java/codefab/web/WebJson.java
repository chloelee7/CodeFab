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
        return new Parser(json).parseSourceObject();
    }

    static String runResult(RunResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":").append(result.success());
        json.append(",\"output\":").append(stringArray(result.output()));
        json.append(",\"diagnostics\":[");
        for (int i = 0; i < result.diagnostics().size(); i++) {
            if (i > 0) {
                json.append(',');
            }
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
            if (!first) {
                json.append(',');
            }
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
            if (!isAtEnd()) {
                throw new JsonException("Unexpected content after JSON object.");
            }
            if (source == null) {
                throw new JsonException("Missing source.");
            }
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
                    if (isAtEnd()) {
                        throw new JsonException("Unterminated escape sequence.");
                    }
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
            while (!isAtEnd() && Character.isWhitespace(peek())) {
                current++;
            }
        }

        private boolean consumeIf(char expected) {
            if (peek() != expected) {
                return false;
            }
            current++;
            return true;
        }

        private void consume(char expected, String message) {
            if (!consumeIf(expected)) {
                throw new JsonException(message);
            }
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
