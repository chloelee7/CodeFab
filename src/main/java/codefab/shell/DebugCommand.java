package codefab.shell;

@FunctionalInterface
public interface DebugCommand {
    boolean execute(DebugShell shell, String argument);
}
