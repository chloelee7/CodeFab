package codefab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import codefab.core.Expr;
import codefab.core.OutputSink;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import codefab.executor.Environment;
import codefab.executor.Executor;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class ExecutorTest {

    @Mock
    OutputSink output;

    Executor executor;

    @BeforeEach
    void setUp() {
        executor = new Executor(output, new Environment());
    }

    private static Token tok(TokenType type, String lexeme) {
        return new Token(type, lexeme, null, 1);
    }

    private static Expr lit(Object value) {
        return new Expr.Literal(value);
    }

    private static Stmt print(Expr expr) {
        return new Stmt.PrintStmt(expr);
    }

    private static Stmt exprStmt(Expr expr) {
        return new Stmt.ExpressionStmt(expr);
    }

    private void run(Stmt... statements) {
        executor.execute(List.of(statements));
    }
}
