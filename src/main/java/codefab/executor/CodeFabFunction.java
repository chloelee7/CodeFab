package codefab.executor;

import codefab.core.Stmt;

/**
 * 런타임 함수 객체. 선언 시점의 Environment(클로저)를 캡처한다.
 */
class CodeFabFunction {
    final Stmt.FunctionStmt declaration;
    final Environment closure;

    CodeFabFunction(Stmt.FunctionStmt declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    int arity() {
        return declaration.params.size();
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
