package codefab.assembler;

/**
 * Internal control-flow signal used by the Parser to unwind to a synchronization
 * point. The actual diagnostic is recorded on the Parser before this is thrown.
 */
class ParseError extends RuntimeException {
    ParseError() {
        super(null, null, false, false);
    }
}
