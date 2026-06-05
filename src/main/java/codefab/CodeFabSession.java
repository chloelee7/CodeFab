package codefab;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.CheckResult;
import codefab.checker.Checker;
import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.InterpreterRuntimeError;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.executor.Environment;
import codefab.executor.Executor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A persistent interpreter session for REPL use. Variables defined in one
 * {@link #run} survive into later runs because the global {@link Environment}
 * is kept alive. Each run uses a fresh {@link Checker} (so a global declared in
 * an earlier input is not a duplicate of one declared now) and reports only the
 * output produced by that input.
 */
public final class CodeFabSession {
    private final Environment globals = new Environment();
    private final CollectingOutputSink output = new CollectingOutputSink();
    // Static-binding distances accumulated ACROSS runs. This must persist (not be
    // replaced per run): a function declared in one REPL input is stored in globals
    // and may be *called* in a later input, at which point its body's local-variable
    // nodes (resolved when the function was declared) must still carry their
    // distances. Keys are AST node identities, so a per-parse node lives in exactly
    // one run's map and merging never corrupts a distance. References the map omits
    // (variables defined in an earlier run) fall back to globals.
    private final Map<Expr, Integer> locals = new IdentityHashMap<>();
    private final Executor executor = new Executor(output, globals, locals);

    /** Run one chunk of source through Assembler -> Checker -> Executor. */
    public RunResult run(String source) {
        output.clear();
        List<Diagnostic> diagnostics = new ArrayList<>();

        // 1. Assembler: scan and parse. Any problem here is a syntax error.
        List<Token> tokens = new Scanner(source, diagnostics).scanTokens();
        List<Stmt> statements = new Parser(tokens, diagnostics).parse();
        if (!diagnostics.isEmpty()) {
            return failure(diagnostics);
        }

        // 2. Checker: static analysis + constant folding + variable resolution.
        //    Do not execute if it complains. The folded program and locals map are
        //    returned in a CheckResult; the Executor runs the folded program.
        CheckResult checked = new Checker(diagnostics).check(statements);
        if (!diagnostics.isEmpty()) {
            return failure(diagnostics);
        }

        // 3. Executor: run the (folded) program. Merge this run's static-binding
        //    distances into the persistent map (keys are this run's folded AST
        //    nodes) so earlier runs' resolved nodes — e.g. a stored function's body
        //    referenced when it is called now — keep their distances. Names the map
        //    omits fall back to the persistent globals.
        locals.putAll(checked.locals());
        executor.setLocals(locals);
        try {
            executor.execute(checked.program());
        } catch (InterpreterRuntimeError error) {
            diagnostics.add(new Diagnostic(Diagnostic.Stage.RUNTIME, error.line(), error.getMessage()));
            return failure(diagnostics);
        }

        return new RunResult(true, output.lines(), diagnostics);
    }

    private RunResult failure(List<Diagnostic> diagnostics) {
        return new RunResult(false, output.lines(), diagnostics);
    }
}
