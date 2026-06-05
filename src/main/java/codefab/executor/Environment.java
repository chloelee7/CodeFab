package codefab.executor;

import codefab.core.InterpreterRuntimeError;
import codefab.core.Token;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A variable store for one scope. Environments form a chain via {@code enclosing};
 * lookups walk outward toward the global scope, so inner scopes can read and
 * assign outer variables while shadowing them locally.
 *
 * <p>Static binding (see shared-contracts §9-1) gives O(1) access: once the
 * Checker has computed the distance to a variable's declaring scope, the
 * Executor uses {@link #getAt}/{@link #assignAt} to jump exactly that many hops
 * via {@link #ancestor} instead of searching the chain.
 *
 * <p><b>Not {@code final} on purpose.</b> Every single hop up the chain goes
 * through {@link #step()}, which is {@code protected} so a test-double subclass
 * can override it to count enclosing traversals and assert that distance-based
 * access never walks more than {@code distance} hops.
 */
public class Environment {
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
            return step().get(name);
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
            step().assign(name, value);
            return;
        }
        throw new InterpreterRuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    // --- static binding (distance) access ----------------------------------

    /**
     * The single seam for moving one hop up the chain. Every chain traversal in
     * this class routes through here, so a counting test double can observe the
     * exact number of enclosing hops a lookup performs without affecting
     * production behaviour. Returns the immediately enclosing scope.
     */
    protected Environment step() {
        return enclosing;
    }

    /**
     * The Environment {@code distance} hops outward (0 = this one). Walks exactly
     * {@code distance} hops via {@link #step()} and never further, so a resolved
     * variable access is O(1) in the distance the Checker computed.
     */
    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.step();
        }
        return environment;
    }

    /** Read a resolved binding directly at {@code distance}, no chain search. */
    public Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    /** Assign a resolved binding directly at {@code distance}, no chain search. */
    public void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    // --- debugger inspection API (contract §10-1) ---------------------------

    /**
     * A read-only snapshot of <em>this</em> scope's variable bindings
     * (name -> value). The enclosing scope is not included. Used by the
     * debugger's {@code watch}/{@code inspect} commands. The returned map is an
     * unmodifiable copy, so callers cannot mutate the live scope.
     */
    public Map<String, Object> bindings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** The enclosing (outer) scope, or {@code null} at the global scope. */
    public Environment enclosing() {
        return enclosing;
    }
}
