package codefab;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.Checker;
import codefab.core.Diagnostic;
import codefab.core.InterpreterRuntimeError;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.executor.Environment;
import codefab.executor.Executor;

import java.util.ArrayList;
import java.util.List;

public final class CodeFabSession {

    private final Environment globals = new Environment();
    private final CollectingOutputSink output = new CollectingOutputSink();
    private final Executor executor = new Executor(output, globals);

    public RunResult run(String source) {
        output.clear();
        List<Diagnostic> diagnostics = new ArrayList<>();

        // 1. Assembler: scan and parse. Any problem here is a syntax error.
        List<Token> tokens = new Scanner(source, diagnostics).scanTokens();
        List<Stmt> statements = new Parser(tokens, diagnostics).parse();
        if (!diagnostics.isEmpty()) {
            return failure(diagnostics);
        }

        // 2. Checker: static semantic analysis. Do not execute if it complains.
        new Checker(diagnostics).check(statements);
        if (!diagnostics.isEmpty()) {
            return failure(diagnostics);
        }

        // 3. Executor: run the program, capturing runtime faults as diagnostics.
        try {
            executor.execute(statements);
        } catch (InterpreterRuntimeError error) {
            diagnostics.add(
                new Diagnostic(Diagnostic.Stage.RUNTIME, error.line(), error.getMessage()));
            return failure(diagnostics);
        }

        return new RunResult(true, output.lines(), diagnostics);
    }

    private RunResult failure(List<Diagnostic> diagnostics) {
        return new RunResult(false, output.lines(), diagnostics);
    }
}
