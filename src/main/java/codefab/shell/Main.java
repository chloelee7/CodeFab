package codefab.shell;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class Main {

    private static final int EX_USAGE = 64;

    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int code = dispatch(args, reader, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int dispatch(String[] args, BufferedReader in, PrintStream out, PrintStream err) {
        // --help/-h: 사용법을 stdout에 찍고 성공(0)으로 종료.
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            printUsage(out);
            return 0;
        }
        Mode mode = select(args);
        if (mode == null) {
            // 잘못된 인자 조합: 사용법을 stderr에 찍고 EX_USAGE(64)로 종료.
            printUsage(err);
            return EX_USAGE;
        }
        return mode.execute(in, out, err);
    }

    private static Mode select(String[] args) {
        if (args.length == 0) {
            return new ReplMode();
        }

        String first = args[0];
        // run/debug는 정확히 파일 인자 1개를 요구한다. 어긋나면 하위호환 경로로
        // 새지 않고 사용법 오류(null)로 처리한다.
        if (first.equals("run")) {
            return args.length == 2 ? new RunMode(args[1]) : null;
        }
        if (first.equals("debug")) {
            return args.length == 2 ? new DebugMode(args[1]) : null;
        }
        if (first.equals("compare")) {
            return args.length == 2 ? new CompareMode(args[1]) : null;
        }
        if (first.equals("selfhost")) {
            if (args.length == 2) {
                return new SelfHostRunMode(args[1]);
            }
            if (args.length == 3 && args[1].equals("run")) {
                return new SelfHostRunMode(args[2]);
            }
            return null;
        }
        // 하위호환: 서브커맨드가 아닌 단일 인자는 파일 경로로 처리.
        if (args.length == 1) {
            return new RunMode(first);
        }

        return null;
    }

    private static void printUsage(PrintStream out) {
        out.println("CodeFab Interpreter");
        out.println();
        out.println("Usage:");
        out.println("  factory                       Start the interactive REPL");
        out.println("  factory run <file>            Run a CodeFab script file with the Java interpreter");
        out.println("  factory selfhost run <file>   Run a CodeFab script file with the CodeFab-written selfhost interpreter");
        out.println("  factory selfhost <file>       Alias for factory selfhost run <file>");
        out.println("  factory compare <file>        Compare Java and selfhost interpreter results");
        out.println("  factory debug <file>          Debug a CodeFab script file (step/break/watch/inspect)");
        out.println("  factory --help                Show this help");
    }
}
