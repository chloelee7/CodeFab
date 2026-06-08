package codefab.executor;

import codefab.core.Token;
import java.util.List;

interface CodeFabCallable {
    int arity();

    Object call(Executor executor, Token token, List<Object> arguments);
}
