package codefab;

import codefab.shell.DebugShell;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DebugShell 기반 테스트의 공용 구동 헬퍼.
 *
 * <p>소스를 임시 파일에 쓰고, 디버그 명령 라인을 stdin으로 주입한 뒤 stdout을 UTF-8 문자열로
 * 캡처해 돌려준다. err 스트림은 어떤 단언에서도 읽히지 않으므로 명시적 null sink로 버린다.
 *
 * <p>DebugShellTest와 BuiltinScopeTest가 이 헬퍼를 공유한다 — DebugShell 시그니처/인코딩이
 * 바뀌면 이 한 곳만 고치면 된다.
 */
final class DebugShellTestSupport {

    private DebugShellTestSupport() {
    }

    /**
     * @param tempDir  스크립트를 쓸 임시 디렉터리(테스트의 {@code @TempDir} 필드)
     * @param source   임시 파일에 기록할 CodeFab 소스
     * @param commands stdin으로 주입할 디버그 명령(개행 구분)
     * @return DebugShell이 stdout으로 출력한 전체 문자열
     */
    static String drive(Path tempDir, String source, String commands) throws IOException {
        Path script = tempDir.resolve("program.cfab");
        Files.writeString(script, source, StandardCharsets.UTF_8);

        BufferedReader in = new BufferedReader(new StringReader(commands));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        // err는 읽지 않으므로 명시적 null sink로 버린다.
        PrintStream err = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        new DebugShell(in, out, err, script.toString()).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
