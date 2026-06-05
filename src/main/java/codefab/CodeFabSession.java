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
    private final Executor executor = new Executor(output, globals);

    public RunResult run(String source) {
        output.clear();
        List<Diagnostic> diagnostics = new ArrayList<>();

        // 1. 어셈블: 스캔 + 파싱
        List<Stmt> statements = assemble(source, diagnostics);
        if (!diagnostics.isEmpty()) {
            return failure(diagnostics);
        }

        // 2. Checker: 정적 의미 분석
        new Checker(diagnostics).check(statements);
        if (!diagnostics.isEmpty()) {
            return failure(diagnostics);
        }

        // 3. ConstantFolder: 상수 폴딩 최적화
        ConstantFolder folder = new ConstantFolder();
        List<Diagnostic> foldDiags = new ArrayList<>();
        statements = folder.fold(statements, foldDiags);
        if (!foldDiags.isEmpty()) {
            diagnostics.addAll(foldDiags);
            return failure(diagnostics);
        }

        // 4. Executor: 실행
        return execute(statements, diagnostics);
    }

    /**
     * 파싱 + Checker 결과만 반환. DebugShell에서 AST 단계 접근에 사용.
     */
    public List<Stmt> getStatements(String source) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<Stmt> statements = assemble(source, diagnostics);
        if (diagnostics.isEmpty()) {
            new Checker(diagnostics).check(statements);
        }
        return statements;
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
