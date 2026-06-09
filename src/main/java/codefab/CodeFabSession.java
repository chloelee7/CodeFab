package codefab;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.Checker;
import codefab.checker.ConstantFolder;
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
    private final Executor executor;

    public CodeFabSession() {
        this(Executor.DEFAULT_MAX_CALL_DEPTH);
    }

    CodeFabSession(int maxCallDepth) {
        this.executor = new Executor(output, globals, maxCallDepth);
    }

    public RunResult run(String source) {
        output.clear();
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<Stmt> statements = assemble(source, diagnostics);
        if (!diagnostics.isEmpty()) {
            return failure(diagnostics);
        }

        new Checker(diagnostics).check(statements);
        if (!diagnostics.isEmpty()) {
            return failure(diagnostics);
        }

        statements = new ConstantFolder().fold(statements);

        return execute(statements, diagnostics);
    }

    public List<Stmt> getStatements(String source, List<Diagnostic> diagnostics) {
        List<Stmt> statements = assemble(source, diagnostics);
        if (!diagnostics.isEmpty()) {
            return statements;
        }
        new Checker(diagnostics).check(statements);
        if (!diagnostics.isEmpty()) {
            return statements;
        }
        return new ConstantFolder().fold(statements);
    }


    private List<Stmt> assemble(String source, List<Diagnostic> diagnostics) {
        List<Token> tokens = new Scanner(source, diagnostics).scanTokens();
        return new Parser(tokens, diagnostics).parse();
    }

    private RunResult execute(List<Stmt> statements, List<Diagnostic> diagnostics) {
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
