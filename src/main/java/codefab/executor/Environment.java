package codefab.executor;

import codefab.core.InterpreterRuntimeError;
import codefab.core.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    public Environment() {
        this.enclosing = null;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

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

    // ── 정적 바인딩 지원 (Phase 2-C) ──────────────────────────────────────────

    public Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    public void assignAt(int distance, String name, Object value) {
        ancestor(distance).values.put(name, value);
    }

    private Environment ancestor(int distance) {
        Environment env = this;
        for (int i = 0; i < distance; i++) {
            env = env.enclosing;
        }
        return env;
    }

    // ── DebugShell 지원 ────────────────────────────────────────────────────────

    /**
     * 현재 스코프부터 전역까지 모든 변수를 덤프한다.
     * DebugShell의 inspect 명령에서 사용한다.
     */
    public List<VarInfo> dumpAll() {
        List<VarInfo> result = new ArrayList<>();
        collectVars(this, true, result);
        return result;
    }

    private void collectVars(Environment env, boolean isLocal, List<VarInfo> result) {
        for (Map.Entry<String, Object> entry : env.values.entrySet()) {
            result.add(new VarInfo(entry.getKey(), entry.getValue(), isLocal));
        }
        if (env.enclosing != null) {
            collectVars(env.enclosing, false, result);
        }
    }

    public static final class VarInfo {
        public final String name;
        public final Object value;
        public final boolean isLocal;

        VarInfo(String name, Object value, boolean isLocal) {
            this.name = name;
            this.value = value;
            this.isLocal = isLocal;
        }

        public String typeName() {
            if (value instanceof Double) return "Number";
            if (value instanceof Boolean) return "Boolean";
            if (value instanceof String) return "String";
            if (value instanceof List) return "Array";
            if (value == null) return "nil";
            return value.getClass().getSimpleName();
        }
    }
}
