package codefab.shell;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintStream;

/**
 * JLine 3 기반 {@link LineSource}. 프로그램이 입력 라인을 소유하여 ↑↓ 히스토리와
 * wide-char(한글) 백스페이스 재그리기를 직접 처리한다(스펙 07).
 *
 * <p>히스토리는 in-memory {@link DefaultHistory}로 세션 내 ↑↓ 동작에 충분하다.
 *
 * <p>키 처리:
 * <ul>
 *   <li>Ctrl-D({@link EndOfFileException}) → null(EOF) → 세션 종료(기존 EOF 흐름).</li>
 *   <li>Ctrl-C({@link UserInterruptException}) → 빈 문자열 → 현재 입력 라인만 취소.
 *       셸의 "빈 입력 무시" 경로가 이어받아 프롬프트를 재표시한다(세션은 유지).</li>
 * </ul>
 */
public final class JLineLineSource implements LineSource {

    private final Terminal terminal;
    private final LineReader lineReader;
    private final PrintStream out;

    private JLineLineSource(Terminal terminal, LineReader lineReader, PrintStream out) {
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.out = out;
    }

    /**
     * system Terminal/LineReader를 생성한다. 실패하면 IOException을 던진다(팩토리가 폴백).
     *
     * @param out 셸이 출력에 쓰는 스트림. {@link #readLine(String)} 직전 flush하여,
     *            {@code out}에 쓴 내용(정지 메시지·watch·프로그램 출력)이 JLine 프롬프트보다
     *            먼저 화면에 나오도록 순서를 고정한다.
     */
    static JLineLineSource create(PrintStream out) throws IOException {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(new DefaultHistory())
                .build();
        return new JLineLineSource(terminal, lineReader, out);
    }

    @Override
    public String readLine(String prompt) {
        // out과 JLine terminal은 서로 다른 버퍼/flush 주기를 가질 수 있으므로,
        // 프롬프트를 그리기 전에 out에 쌓인 내용을 먼저 비워 출력 순서를 못박는다.
        out.flush();
        try {
            return lineReader.readLine(prompt);
        } catch (EndOfFileException e) {
            // Ctrl-D → EOF로 취급(세션 종료).
            return null;
        } catch (UserInterruptException e) {
            // Ctrl-C → 현재 라인만 취소. 빈 입력으로 돌려보내 프롬프트를 재표시한다(세션 유지).
            return "";
        }
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException ignored) {
            // close 실패는 무시한다.
        }
    }
}
