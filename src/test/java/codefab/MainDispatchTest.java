package codefab;

import codefab.shell.Main;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Main 모드 디스패치")
class MainDispatchTest {

    private String out;
    private String err;

    private int dispatch(String[] args, String stdin) {
        BufferedReader in = new BufferedReader(new StringReader(stdin));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream errStream = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        int code = Main.dispatch(args, in, outStream, errStream);
        this.out = outBytes.toString(StandardCharsets.UTF_8);
        this.err = errBytes.toString(StandardCharsets.UTF_8);
        return code;
    }

    @Test
    @DisplayName("인자 없음 → REPL 모드 실행")
    void dispatchNoArgs_startsRepl() {
        int code = dispatch(new String[]{}, "var a=5; var b=10; print a+b;\n:exit\n");
        assertTrue(out.contains("15"), () -> "expected 15 in REPL output:\n" + out);
        assertEquals(0, code);
    }

    @Test
    @DisplayName("run <파일> → 스크립트 실행")
    void dispatchRunFile_executesScript(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ok.cf");
        Files.writeString(file, "print 1 + 2;\n");
        int code = dispatch(new String[]{"run", file.toString()}, "");
        assertTrue(out.contains("3"), () -> "expected 3 in output:\n" + out);
        assertEquals(0, code);
    }

    @Test
    @DisplayName("단일 파일 경로 → run과 동일 (하위호환)")
    void dispatchBareFilePath_backwardCompatible(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bare.cf");
        Files.writeString(file, "print 7 + 1;\n");
        int code = dispatch(new String[]{file.toString()}, "");
        assertTrue(out.contains("8"), () -> "bare path should run like 'run':\n" + out);
        assertEquals(0, code);
    }

    @Test
    @DisplayName("selfhost <파일> → selfhost 스크립트 실행")
    void dispatchSelfhostFile_executesScript(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("selfhost.cf");
        Files.writeString(file, "print 4;\n");
        int code = dispatch(new String[]{"selfhost", file.toString()}, "");
        assertEquals("4\n", out);
        assertEquals("", err);
        assertEquals(0, code);
    }

    @Test
    @DisplayName("selfhost run <파일> → selfhost 스크립트 실행")
    void dispatchSelfhostRunFile_executesScript(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("selfhost-run.cf");
        Files.writeString(file, "print 8;\n");
        int code = dispatch(new String[]{"selfhost", "run", file.toString()}, "");
        assertEquals("8\n", out);
        assertEquals("", err);
        assertEquals(0, code);
    }

    @Test
    @DisplayName("파일 없음 → 코드 66, stderr 오류")
    void dispatchMissingFile_returns66() {
        int code = dispatch(new String[]{"run", "/no/such/file.cf"}, "");
        assertTrue(err.contains("Error: file not found:"), () -> "expected not-found on err:\n" + err);
        assertEquals(66, code);
    }

    @Test
    @DisplayName("런타임 오류 → 코드 65")
    void dispatchRuntimeError_returns65(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.cf");
        Files.writeString(file, "print missing;\n");
        int code = dispatch(new String[]{"run", file.toString()}, "");
        assertEquals(65, code, () -> "failing script must yield data-error code; err:\n" + err);
    }

    @Test
    @DisplayName("--help/-h → stdout 사용법, 코드 0")
    void dispatchHelp_printsUsageCode0() {
        int code = dispatch(new String[]{"--help"}, "");
        assertTrue(out.contains("Usage:"), () -> "expected usage:\n" + out);
        assertTrue(out.contains("factory selfhost run <file>"), () -> "expected selfhost usage:\n" + out);
        assertEquals(0, code);

        int codeShort = dispatch(new String[]{"-h"}, "");
        assertTrue(out.contains("Usage:"), () -> "expected usage for -h:\n" + out);
        assertTrue(out.contains("factory selfhost run <file>"), () -> "expected selfhost usage for -h:\n" + out);
        assertEquals(0, codeShort);
    }

    @Test
    @DisplayName("잘못된 인자 조합 → 코드 64(EX_USAGE), stderr 사용법")
    void dispatchBadArgs_returns64UsageOnErr() {
        int code = dispatch(new String[]{"run"}, "");
        assertEquals(64, code, () -> "bad arg combo must yield EX_USAGE; out:\n" + out + "\nerr:\n" + err);
        assertTrue(err.contains("Usage:"), () -> "usage should go to err:\n" + err);
        assertFalse(out.contains("Usage:"), () -> "usage must not go to out on error:\n" + out);
    }

    @Test
    @DisplayName("debug <파일> → 디버거 모드 실행")
    void dispatchDebug_smoke(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("dbg.cf");
        Files.writeString(file, "print 1;\n");
        int code = dispatch(new String[]{"debug", file.toString()}, "continue\nexit\n");
        assertTrue(out.contains("[DEBUG]"), () -> "expected debugger banner:\n" + out);
        assertEquals(0, code);
    }
}
