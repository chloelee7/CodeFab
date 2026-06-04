package codefab;

import codefab.core.OutputSink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CollectingOutputSink implements OutputSink {

    private final List<String> lines = new ArrayList<>();

    @Override
    public void print(String line) {
        lines.add(line);
    }

    public List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    public void clear() {
        lines.clear();
    }
}
