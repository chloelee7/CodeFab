package codefab.executor;

import codefab.core.InterpreterRuntimeError;
import codefab.core.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * A variable store for one scope. Environments form a chain via {@code enclosing};
 * lookups walk outward toward the global scope, so inner scopes can read and
 * assign outer variables while shadowing them locally.
 */
public final class Environment {
    private final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    public Environment() {
        this.enclosing = null;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    /** Declare or overwrite a binding in this scope. */
    public void define(String name, Object value) {
        values.put(name, value);
    }

    public Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }
        if (enclosing != null) {
            return enclosing.get(name);
        }
        throw new InterpreterRuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    /** Assign to an existing binding, searching outward. Never creates new bindings. */
    public void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }
        throw new InterpreterRuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }
}
