package codefab;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** REPL semantics: a session keeps variables alive across run() calls. */
class SessionTest {

    @Test
    void variablesPersistAcrossInputs() {
        CodeFabSession session = new CodeFabSession();
        assertTrue(session.run("var a = 5;").success());
        assertTrue(session.run("var b = 10;").success());
        RunResult r = session.run("print a + b;");
        assertTrue(r.success(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(List.of("15"), r.output());
    }

    @Test
    void eachRunReportsOnlyItsOwnOutput() {
        CodeFabSession session = new CodeFabSession();
        session.run("var x = 1;");
        RunResult r = session.run("print x;");
        assertEquals(List.of("1"), r.output());
    }

    @Test
    void redeclaringExistingGlobalAcrossInputsIsAllowedInRepl() {
        // A fresh top-level scope per input means re-declaring `a` in a later
        // input is not a duplicate within a single scope.
        CodeFabSession session = new CodeFabSession();
        assertTrue(session.run("var a = 1;").success());
        RunResult r = session.run("var a = 2; print a;");
        assertTrue(r.success(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(List.of("2"), r.output());
    }

    @Test
    void runtimeErrorInOneInputDoesNotKillSession() {
        CodeFabSession session = new CodeFabSession();
        session.run("var a = 1;");
        assertFalse(session.run("print missing;").success());
        RunResult r = session.run("print a;");
        assertTrue(r.success());
        assertEquals(List.of("1"), r.output());
    }
}
