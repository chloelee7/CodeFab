package codefab.executor;

import codefab.core.Token;

public final class Environment {
    private final Environment enclosing;

    public Environment() {
        this.enclosing = null;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public void define(String name, Object value) {
        throw new UnsupportedOperationException("define not implemented");
    }

    public Object get(Token name) {
        throw new UnsupportedOperationException("get not implemented");
    }

    public void assign(Token name, Object value) {
        throw new UnsupportedOperationException("assign not implemented");
    }
}
