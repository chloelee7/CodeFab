package codefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codefab.shell.Main;
import codefab.core.Diagnostic;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfHostCodeFabTest {
    private static CapturedMain captureMain(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8));

            Main.main(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new CapturedMain(
            outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
            errBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private record CapturedMain(String out, String err) {
    }

    @Test
    @DisplayName("SelfHostCodeFab executes valid programs through the selfhost runner")
    void executesValidProgramsThroughSelfhostRunner() {
        RunResult result = new SelfHostCodeFab().run(
            "var sum = 0; for (var i = 0; i < 3; i = i + 1) { sum = sum + i; } print sum;");

        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        assertEquals(List.of("3"), result.output());
        assertEquals(List.of(), result.diagnostics());
    }

    @Test
    @DisplayName("SelfHostCodeFab embeds quoted source strings")
    void embedsQuotedSourceStrings() {
        RunResult result = new SelfHostCodeFab().run("print \"hi\";");

        assertTrue(result.success(), () -> "expected success, got diagnostics: " + result.diagnostics());
        assertEquals(List.of("hi"), result.output());
    }

    @Test
    @DisplayName("SelfHostCodeFab converts checker diagnostics to RunResult diagnostics")
    void convertsCheckerDiagnostics() {
        RunResult result = new SelfHostCodeFab().run("return 1;");

        assertEquals(false, result.success());
        assertEquals(List.of(), result.output());
        assertEquals(1, result.diagnostics().size());
        assertEquals(Diagnostic.Stage.CHECKER, result.diagnostics().get(0).stage);
        assertEquals(1, result.diagnostics().get(0).line);
        assertEquals("Can't return from top-level code.", result.diagnostics().get(0).message);
    }

    @Test
    @DisplayName("SelfHostCodeFab converts scanner diagnostics to RunResult diagnostics")
    void convertsScannerDiagnostics() {
        RunResult result = new SelfHostCodeFab().run("@");

        assertEquals(false, result.success());
        assertEquals(List.of(), result.output());
        assertEquals(1, result.diagnostics().size());
        assertEquals(Diagnostic.Stage.SCANNER, result.diagnostics().get(0).stage);
        assertEquals(1, result.diagnostics().get(0).line);
        assertEquals("unexpected character '@'", result.diagnostics().get(0).message);
    }

    @Test
    @DisplayName("SelfHostCodeFab converts parser diagnostics to RunResult diagnostics")
    void convertsParserDiagnostics() {
        RunResult result = new SelfHostCodeFab().run("print 1");

        assertEquals(false, result.success());
        assertEquals(List.of(), result.output());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
            d.stage == Diagnostic.Stage.PARSER
                && d.line == 1
                && d.message.equals("Expect ';' after value. at end")));
    }

    @Test
    @DisplayName("SelfHostCodeFab converts runtime diagnostics to RunResult diagnostics")
    void convertsRuntimeDiagnostics() {
        RunResult result = new SelfHostCodeFab().run("print 1;\nprint missing;");

        assertEquals(false, result.success());
        assertEquals(List.of("1"), result.output());
        assertEquals(1, result.diagnostics().size());
        assertEquals(Diagnostic.Stage.RUNTIME, result.diagnostics().get(0).stage);
        assertEquals(2, result.diagnostics().get(0).line);
        assertEquals("Undefined variable 'missing'.", result.diagnostics().get(0).message);
    }

    @Test
    @DisplayName("SelfHostCodeFab converts call runtime diagnostics")
    void convertsCallRuntimeDiagnostics() {
        RunResult result = new SelfHostCodeFab().run("print 3();");

        assertEquals(false, result.success());
        assertEquals(1, result.diagnostics().size());
        assertEquals(Diagnostic.Stage.RUNTIME, result.diagnostics().get(0).stage);
        assertEquals(1, result.diagnostics().get(0).line);
        assertEquals("Can only call functions.", result.diagnostics().get(0).message);
    }

    @Test
    @DisplayName("Main exposes the selfhost file runner")
    void mainExposesSelfhostFileRunner() throws IOException {
        Path script = Files.createTempFile("codefab-selfhost", ".cfab");
        Files.writeString(script, "print 4;", StandardCharsets.UTF_8);

        try {
            CapturedMain captured = captureMain("selfhost", script.toString());
            assertEquals("4\n", captured.out());
            assertEquals("", captured.err());
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Test
    @DisplayName("Main accepts selfhost run file command")
    void mainAcceptsSelfhostRunFileCommand() throws IOException {
        Path script = Files.createTempFile("codefab-selfhost-run", ".cfab");
        Files.writeString(script, "print 8;", StandardCharsets.UTF_8);

        try {
            CapturedMain captured = captureMain("selfhost", "run", script.toString());
            assertEquals("8\n", captured.out());
            assertEquals("", captured.err());
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Test
    @DisplayName("Main help explains Java and selfhost run commands")
    void mainHelpExplainsJavaAndSelfhostRunCommands() {
        CapturedMain captured = captureMain("--help");

        assertTrue(captured.out().contains("factory run <file>"));
        assertTrue(captured.out().contains("factory selfhost run <file>"));
        assertTrue(captured.out().contains("Java interpreter"));
        assertTrue(captured.out().contains("CodeFab-written selfhost interpreter"));
        assertEquals("", captured.err());
    }

    @Test
    @DisplayName("SelfHostCodeFab reports malformed selfhost result as diagnostic")
    void reportsMalformedSelfhostResultAsDiagnostic() throws IOException {
        Path bootstrap = Files.createTempFile("codefab-selfhost-malformed", ".cfab");
        Files.writeString(bootstrap, """
            Func run(source) {
                print "__CODEFAB_SELFHOST_RESULT_V1__";
                print true;
                var result = Array(3);
                result[0] = true;
                result[1] = Array(0);
                result[2] = Array(0);
                return result;
            }
            print "__CODEFAB_SELFHOST_RESULT_V1__";
            print true;
            """, StandardCharsets.UTF_8);

        try {
            RunResult result = new SelfHostCodeFab(List.of(bootstrap)).run("print 1;");

            assertEquals(false, result.success());
            assertEquals(1, result.diagnostics().size());
            assertEquals(Diagnostic.Stage.RUNTIME, result.diagnostics().get(0).stage);
            assertEquals("Selfhost runner emitted malformed result.", result.diagnostics().get(0).message);
        } finally {
            Files.deleteIfExists(bootstrap);
        }
    }

    @Test
    @DisplayName("SelfHostCodeFab preserves commas inside diagnostic messages")
    void preservesCommasInsideDiagnosticMessages() throws IOException {
        Path bootstrap = Files.createTempFile("codefab-selfhost-comma-diagnostic", ".cfab");
        Files.writeString(bootstrap, """
            Func run(source) {
                var diagnostic = Array(3);
                diagnostic[0] = "RUNTIME";
                diagnostic[1] = 7;
                diagnostic[2] = "message with comma, still intact";

                var diagnostics = Array(1);
                diagnostics[0] = diagnostic;

                var result = Array(3);
                result[0] = false;
                result[1] = Array(0);
                result[2] = diagnostics;
                return result;
            }
            """, StandardCharsets.UTF_8);

        try {
            RunResult result = new SelfHostCodeFab(List.of(bootstrap)).run("print 1;");

            assertEquals(false, result.success());
            assertEquals(1, result.diagnostics().size());
            assertEquals(Diagnostic.Stage.RUNTIME, result.diagnostics().get(0).stage);
            assertEquals(7, result.diagnostics().get(0).line);
            assertEquals("message with comma, still intact", result.diagnostics().get(0).message);
        } finally {
            Files.deleteIfExists(bootstrap);
        }
    }

    @Test
    @DisplayName("selfhost bootstrap files are packaged as classpath resources")
    void selfhostBootstrapFilesArePackagedAsClasspathResources() {
        ClassLoader loader = SelfHostCodeFab.class.getClassLoader();

        assertNotNull(loader.getResource("selfhost/scanner.cfab"));
        assertNotNull(loader.getResource("selfhost/parser.cfab"));
        assertNotNull(loader.getResource("selfhost/checker.cfab"));
        assertNotNull(loader.getResource("selfhost/executor.cfab"));
        assertNotNull(loader.getResource("selfhost/runner.cfab"));
    }
}
