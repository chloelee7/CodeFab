package codefab;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.CheckResult;
import codefab.checker.Checker;
import codefab.core.Diagnostic;
import codefab.core.InterpreterRuntimeError;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.executor.Environment;
import codefab.executor.Executor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
    // Built with the resolved (3-arg) constructor so distance-based O(1) variable
    // access is enabled. The persistent globals are kept across runs; each run's
    // fresh locals map is swapped in via setLocals() below. References the map
    // omits (e.g. variables defined in an earlier REPL run) fall back to globals.
    private final Executor executor = new Executor(output, globals, new HashMap<>());

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

        // 3. Executor: run the (folded) program with this run's static-binding
        //    distances, capturing runtime faults. The distance map is per-run
        //    (its keys are this run's folded AST nodes); swap it in before
        //    executing. Names it omits fall back to the persistent globals, so
        //    variables defined in earlier REPL runs are still resolved.
        executor.setLocals(checked.locals());
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
