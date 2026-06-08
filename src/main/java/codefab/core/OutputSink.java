package codefab.core;

/**
 * Sink for program output. Injected so core execution never touches
 * {@code System.out} directly and tests can capture printed lines.
 */
@FunctionalInterface
public interface OutputSink {
    void print(String line);
}
