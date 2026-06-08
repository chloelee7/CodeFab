package codefab;

import codefab.shell.Main;
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

/**
 * Behaviour-preservation tests for the Mode=Strategy refactor: drive
 * {@link Main#dispatch} directly (no {@code System.exit}) and assert the exit
 * code and out/err output for each mode.
 */
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
    void dispatchNoArgs_startsRepl() {
        int code = dispatch(new String[]{}, "var a=5; var b=10; print a+b;\n:exit\n");
        assertTrue(out.contains("15"), () -> "expected 15 in REPL output:\n" + out);
        assertEquals(0, code);
    }

    @Test
    void dispatchRunFile_executesScript(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ok.cf");
        Files.writeString(file, "print 1 + 2;\n");
        int code = dispatch(new String[]{"run", file.toString()}, "");
        assertTrue(out.contains("3"), () -> "expected 3 in output:\n" + out);
        assertEquals(0, code);
    }

    @Test
    void dispatchBareFilePath_backwardCompatible(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bare.cf");
        Files.writeString(file, "print 7 + 1;\n");
        int code = dispatch(new String[]{file.toString()}, "");
        assertTrue(out.contains("8"), () -> "bare path should run like 'run':\n" + out);
        assertEquals(0, code);
    }

    @Test
    void dispatchMissingFile_returns66() {
        int code = dispatch(new String[]{"run", "/no/such/file.cf"}, "");
        assertTrue(err.contains("Error: file not found:"), () -> "expected not-found on err:\n" + err);
        assertEquals(66, code);
    }

    @Test
    void dispatchRuntimeError_returns65(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.cf");
        Files.writeString(file, "print missing;\n");
        int code = dispatch(new String[]{"run", file.toString()}, "");
        assertEquals(65, code, () -> "failing script must yield data-error code; err:\n" + err);
    }

    @Test
    void dispatchHelp_printsUsageCode0() {
        int code = dispatch(new String[]{"--help"}, "");
        assertTrue(out.contains("Usage:"), () -> "expected usage:\n" + out);
        assertEquals(0, code);

        int codeShort = dispatch(new String[]{"-h"}, "");
        assertTrue(out.contains("Usage:"), () -> "expected usage for -h:\n" + out);
        assertEquals(0, codeShort);
    }

    @Test
    void dispatchBadArgs_returns64UsageOnErr() {
        int code = dispatch(new String[]{"run"}, "");
        assertEquals(64, code, () -> "bad arg combo must yield EX_USAGE; out:\n" + out + "\nerr:\n" + err);
        assertTrue(err.contains("Usage:"), () -> "usage should go to err:\n" + err);
        assertFalse(out.contains("Usage:"), () -> "usage must not go to out on error:\n" + out);
    }

    @Test
    void dispatchDebug_smoke(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("dbg.cf");
        Files.writeString(file, "print 1;\n");
        int code = dispatch(new String[]{"debug", file.toString()}, "continue\nexit\n");
        assertTrue(out.contains("[DEBUG]"), () -> "expected debugger banner:\n" + out);
        assertEquals(0, code);
    }
}
