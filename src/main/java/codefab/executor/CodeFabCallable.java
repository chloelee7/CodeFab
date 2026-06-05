package codefab.executor;

import java.util.List;

/**
 * Strategy interface for anything callable at runtime: user-defined functions
 * ({@link CodeFabFunction}) and native callables. {@code arity} is the number of
 * parameters the call expects; {@code call} runs it with the evaluated arguments.
 */
public interface CodeFabCallable {
    int arity();

    Object call(Executor executor, List<Object> arguments);
}
