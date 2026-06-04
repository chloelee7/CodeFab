package codefab;

public final class CodeFab {

    public RunResult run(String source) {
        return new CodeFabSession().run(source);
    }
}
