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
    /** builtin 스코프(네이티브 함수 전용)인지 여부. 기본 false. (계약 §8-0) */
    private final boolean builtin;

    public Environment() {
        this.enclosing = null;
        this.builtin = false;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
        this.builtin = false;
    }

    private Environment(Environment enclosing, boolean builtin) {
        this.enclosing = enclosing;
        this.builtin = builtin;
    }

    /** builtin 스코프(네이티브 함수 전용)를 생성한다. (계약 §8-0) */
    public static Environment newBuiltinScope() {
        return new Environment(null, true);
    }

    /**
     * 이 환경이 builtin 스코프(네이티브 함수 전용)인지.
     * package-private: 같은 codefab.executor 패키지(예: Executor 생성자 검증)에서만 쓰인다. (계약 §8-0)
     */
    boolean isBuiltinScope() {
        return builtin;
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
        // builtin 스코프(네이티브 함수)는 사용자 대입의 대상이 될 수 없다 (계약 §8-0).
        // 쓰기는 builtin 경계에서 멈추고(values 갱신 안 함), builtin은 체인 끝이므로
        // 미선언 이름이면 결과적으로 Undefined variable 오류가 난다. 읽기(get)는 비대칭으로
        // builtin까지 그대로 허용해 네이티브 호출을 보존한다.
        if (!builtin && values.containsKey(name.lexeme)) {
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
        // builtin 스코프(네이티브 함수)의 바인딩은 inspect/덤프에서 제외하되,
        // 위에 일반 스코프가 더 있을 수 있으므로 재귀는 멈추지 않는다 (계약 §8-0).
        // 현재 체인(builtin=끝)에선 동작 동일, 미래에 builtin 위 스코프가 생겨도 누락 없음.
        if (!env.builtin) {
            for (Map.Entry<String, Object> entry : env.values.entrySet()) {
                result.add(new VarInfo(entry.getKey(), entry.getValue(), isLocal));
            }
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
