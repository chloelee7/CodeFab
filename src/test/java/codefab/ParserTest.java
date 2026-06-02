package codefab;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.core.Diagnostic;
import codefab.core.Stmt;
import codefab.core.Token;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private List<Diagnostic> parseDiagnostics(String src) {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = new Scanner(src, diags).scanTokens();
        new Parser(tokens, diags).parse();
        return diags;
    }

    private List<Stmt> parseOk(String src) {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = new Scanner(src, diags).scanTokens();
        List<Stmt> stmts = new Parser(tokens, diags).parse();
        assertTrue(diags.isEmpty(), () -> "unexpected diagnostics: " + diags);
        return stmts;
    }

    private boolean hasMessage(List<Diagnostic> diags, String substring) {
        return diags.stream().anyMatch(d -> d.message.contains(substring));
    }

    @Test
    void parsesPrintAndVarDeclarations() {
        List<Stmt> stmts = parseOk("var a = 1; print a;");
        assertEquals(2, stmts.size());
        assertInstanceOf(Stmt.VarStmt.class, stmts.get(0));
        assertInstanceOf(Stmt.PrintStmt.class, stmts.get(1));
    }

    @Test
    void parsesBlocksAndControlFlow() {
        List<Stmt> stmts = parseOk("if (true) { print 1; } else print 2; for (;;) print 3;");
        assertInstanceOf(Stmt.IfStmt.class, stmts.get(0));
        assertInstanceOf(Stmt.ForStmt.class, stmts.get(1));
    }

    @Test
    void reportsMissingSemicolon() {
        assertTrue(hasMessage(parseDiagnostics("print 1 + 2"), "Expect ';' after value."));
    }

    @Test
    void reportsMissingClosingParen() {
        assertTrue(hasMessage(parseDiagnostics("print (1 + 2;"), "Expect ')' after expression."));
    }

    @Test
    void reportsInvalidAssignmentTarget() {
        String src = "var a = 1;\nvar b = 2;\na + b = 3;";
        assertTrue(hasMessage(parseDiagnostics(src), "Invalid assignment target."));
    }

    @Test
    void reportsInvalidExpressionStart() {
        assertTrue(hasMessage(parseDiagnostics("print * 5;"), "Expect expression."));
    }
}
