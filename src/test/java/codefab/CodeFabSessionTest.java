package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodeFabSessionTest {

    @Test
    void 변수는_여러_입력에_걸쳐_유지된다() {
        CodeFabSession session = new CodeFabSession();
        assertTrue(session.run("var a = 5;").success());
        assertTrue(session.run("var b = 10;").success());
        RunResult r = session.run("print a + b;");
        assertTrue(r.success(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(List.of("15"), r.output());
    }

    @Test
    void 각_실행은_자신의_출력만_보고한다() {
        CodeFabSession session = new CodeFabSession();
        session.run("var x = 1;");
        RunResult r = session.run("print x;");
        assertEquals(List.of("1"), r.output());
    }

    @Test
    void REPL에서_입력_간_기존_전역변수_재선언이_허용된다() {
        CodeFabSession session = new CodeFabSession();
        assertTrue(session.run("var a = 1;").success());
        RunResult r = session.run("var a = 2; print a;");
        assertTrue(r.success(), () -> "diagnostics: " + r.diagnostics());
        assertEquals(List.of("2"), r.output());
    }

    @Test
    void 한_입력의_런타임_오류가_세션을_죽이지_않는다() {
        CodeFabSession session = new CodeFabSession();
        session.run("var a = 1;");
        assertFalse(session.run("print missing;").success());
        RunResult r = session.run("print a;");
        assertTrue(r.success());
        assertEquals(List.of("1"), r.output());
    }
}
