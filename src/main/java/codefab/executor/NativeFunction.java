package codefab.executor;

abstract class NativeFunction implements CodeFabCallable {
    private final String name;
    private final int arity;

    NativeFunction(String name, int arity) {
        this.name = name;
        this.arity = arity;
    }

    @Override
    public int arity() {
        return arity;
    }

    @Override
    public String toString() {
        return "<native fn " + name + ">";
    }
}
