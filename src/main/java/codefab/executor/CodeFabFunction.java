package codefab.executor;

import codefab.core.Stmt;
import codefab.core.Token;

import java.util.List;

/**
 * A user-defined function value. Holds its declaration and the {@code closure}
 * environment captured at declaration time (contract §8). Calling it opens a
 * fresh scope chained off the closure, binds parameters left-to-right, then runs
 * the body. A {@link Return} thrown inside the body delivers the result; if the
 * body completes without one, the call yields {@code null}.
 */
final class CodeFabFunction implements CodeFabCallable {
    private final Stmt.FunctionStmt declaration;
    private final Environment closure;

    CodeFabFunction(Stmt.FunctionStmt declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Executor executor, List<Object> arguments) {
        Environment env = new Environment(closure);
        List<Token> params = declaration.params;
        for (int i = 0; i < params.size(); i++) {
            env.define(params.get(i).lexeme, arguments.get(i));
        }
        try {
            executor.executeBlock(declaration.body, env);
        } catch (Return r) {
            return r.value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
