package codefab.shell;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * JLine 3 기반 {@link LineSource}. 프로그램이 입력 라인을 소유하여 ↑↓ 히스토리와
 * wide-char(한글) 백스페이스 재그리기를 직접 처리한다(스펙 07).
 *
 * <p>히스토리는 in-memory {@link DefaultHistory}로 세션 내 ↑↓ 동작에 충분하다.
 * Ctrl-D({@link EndOfFileException}) / Ctrl-C({@link UserInterruptException})는 모두
 * null(EOF)로 취급하여 기존 EOF 흐름과 동일하게 종료한다.
 */
public final class JLineLineSource implements LineSource {

    private final Terminal terminal;
    private final LineReader lineReader;

    private JLineLineSource(Terminal terminal, LineReader lineReader) {
        this.terminal = terminal;
        this.lineReader = lineReader;
    }

    /** system Terminal/LineReader를 생성한다. 실패하면 IOException을 던진다(팩토리가 폴백). */
    static JLineLineSource create() throws IOException {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(new DefaultHistory())
                .build();
        return new JLineLineSource(terminal, lineReader);
    }

    @Override
    public String readLine(String prompt) {
        try {
            return lineReader.readLine(prompt);
        } catch (EndOfFileException e) {
            // Ctrl-D → EOF로 취급.
            return null;
        } catch (UserInterruptException e) {
            // Ctrl-C → 종료로 취급(기존 EOF와 동일 흐름).
            return null;
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
