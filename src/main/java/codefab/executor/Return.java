package codefab.executor;

/**
 * Control-flow signal used to unwind out of a function body when a
 * {@code return} statement runs. This is not a diagnostic: it carries the
 * returned value and is caught by {@link CodeFabFunction#call}. The host stack
 * trace is suppressed because it never represents an error.
 */
final class Return extends RuntimeException {
    final Object value;

    Return(Object value) {
        super(null, null, false, false);
        this.value = value;
    }
}
