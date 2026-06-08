package codefab.shell;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 명령행 진입점 — 모드 디스패처(GoF Strategy). 첫 인자로 {@link Mode}를 고른다:
 * <ul>
 *   <li>인자 없음        → {@link ReplMode} (대화형 {@link PromptShell})</li>
 *   <li>{@code run <f>}   → {@link RunMode} (파일 모드)</li>
 *   <li>{@code debug <f>} → {@link DebugMode} (대화형 {@link DebugShell})</li>
 *   <li>{@code --help}/{@code -h} → 사용법</li>
 *   <li>단일 인자(파일 경로) → {@code run <f>}와 동일(하위호환)</li>
 * </ul>
 *
 * <p>{@link #dispatch}는 모드를 골라 실행하고 {@code System.exit} 없이 종료 코드를
 * 반환하므로 단위 테스트가 가능하다. {@link #main}이 그 코드를 프로세스 종료로 매핑한다.
 */
public final class Main {

    /** POSIX EX_USAGE: 잘못된 명령행 인자 조합. */
    private static final int EX_USAGE = 64;

    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int code = dispatch(args, reader, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    /**
     * {@code args}에 맞는 모드를 골라 실행하고 종료 코드를 반환한다(프로세스에 대해 순수).
     */
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
        out.println("  factory               Start the interactive REPL");
        out.println("  factory run <file>    Run a CodeFab script file");
        out.println("  factory debug <file>  Debug a CodeFab script file (step/break/watch/inspect)");
        out.println("  factory --help        Show this help");
    }
}
