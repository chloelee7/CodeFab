package codefab.shell;

import codefab.CodeFab;
import codefab.RunResult;
import codefab.core.Diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {

    private static final int EX_DATA_ERR = 65;
    private static final int EX_NO_INPUT = 66;

    public static void main(String[] args) {
        if (args.length == 0) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
            new PromptShell(reader, System.out).run();
            return;
        }

        if (args[0].equals("--help") || args[0].equals("-h")) {
            printUsage();
            return;
        }

        if (args[0].equals("run") && args.length == 2) {
            runFile(args[1]);
            return;
        }

        if (args[0].equals("debug") && args.length == 2) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
            new DebugShell(reader, System.out, args[1]).run();
            return;
        }

        // 하위호환: 단일 인자는 파일 경로로 처리
        if (args.length == 1) {
            runFile(args[0]);
            return;
        }

        printUsage();
    }

    static void runFile(String path) {
        String source;
        try {
            source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error: file not found: " + path);
            System.exit(EX_NO_INPUT);
            return;
        }

        RunResult result = new CodeFab().run(source);
        for (String line : result.output()) {
            System.out.println(line);
        }
        for (Diagnostic diagnostic : result.diagnostics()) {
            System.err.println(diagnostic.render());
        }
        if (!result.success()) {
            System.exit(EX_DATA_ERR);
        }
    }

    private static void printUsage() {
        System.out.println("CodeFab Interpreter");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  codefab               Start the interactive REPL");
        System.out.println("  codefab run <file>    Run a CodeFab script file");
        System.out.println("  codefab debug <file>  Debug a CodeFab script file");
        System.out.println("  codefab --help        Show this help");
    }
}
