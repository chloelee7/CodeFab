package codefab.executor;

import codefab.core.Stmt;
import codefab.core.Token;

import java.util.List;

/**
 * 런타임 함수 객체. 선언 시점의 Environment(클로저)를 캡처한다.
 */
class CodeFabFunction implements CodeFabCallable {
    final Stmt.FunctionStmt declaration;
    final Environment closure;

    CodeFabFunction(Stmt.FunctionStmt declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params().size();
    }

    @Override
    public Object call(Executor executor, Token token, List<Object> arguments) {
        return executor.callUserFunction(this, token, arguments);
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name().lexeme + ">";
    }
}
