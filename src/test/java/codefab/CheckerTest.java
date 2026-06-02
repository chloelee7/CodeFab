package codefab;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.Checker;
import codefab.core.Diagnostic;
import codefab.core.Stmt;
import codefab.core.Token;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckerTest {

    private List<Diagnostic> check(String src) {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = new Scanner(src, diags).scanTokens();
        List<Stmt> stmts = new Parser(tokens, diags).parse();
        assertTrue(diags.isEmpty(), () -> "parser produced diagnostics: " + diags);
        List<Diagnostic> checkerDiags = new ArrayList<>();
        new Checker(checkerDiags).check(stmts);
        return checkerDiags;
    }

    private boolean has(List<Diagnostic> diags, String substring) {
        return diags.stream().anyMatch(d -> d.message.contains(substring));
    }

    @Test
    void duplicateInSameLocalScopeIsError() {
        assertTrue(has(check("{ var a = 1; var a = 2; }"),
                "Already a variable with this name in this scope."));
    }

    @Test
    void readingVariableInOwnInitializerIsError() {
        assertTrue(has(check("{ var a = a; }"), "Can't read local variable in initializer."));
    }

    @Test
    void shadowingInNestedScopeIsAllowed() {
        assertTrue(check("var a = 1; { var a = 2; print a; }").isEmpty());
    }

    @Test
    void usingDefinedVariableIsFine() {
        assertTrue(check("{ var a = 1; var b = a; print b; }").isEmpty());
    }

    @Test
    void globalDuplicateIsTreatedAsError() {
        assertTrue(has(check("var a = 1; var a = 2;"),
                "Already a variable with this name in this scope."));
    }

    @Test
    void selfInitializingShadowedVariableIsStillAnError() {
        // The inner `b` is DECLARED-but-not-yet-DEFINED while its own initializer
        // is checked, so reading it resolves to the half-declared local -> error,
        // even though an outer `b` exists.
        assertTrue(has(check("var b = 1; { var b = b; print b; }"),
                "Can't read local variable in initializer."));
    }
}
