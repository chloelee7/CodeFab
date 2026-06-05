package codefab.shell;

/**
 * One interactive debugger command (GoF Command). The {@link Debugger} keeps a
 * registry of these keyed by name and dispatches the line the user typed at the
 * {@code >} prompt to the matching command.
 *
 * <p>A command mutates the debugger's state (set a breakpoint, register a watch,
 * choose the next stepping mode, ...) and reports through the debugger's output.
 * Its return value tells the command loop whether to resume execution.
 */
public interface DebugCommand {
    /**
     * Run this command.
     *
     * @param debugger the debugger to act on
     * @param argument the text after the command word (e.g. the line number for
     *                 {@code break 7}); empty string when there is none.
     * @return {@code true} if execution should resume (step/next/continue),
     *         {@code false} to stay at the prompt for another command.
     */
    boolean execute(Debugger debugger, String argument);
}
