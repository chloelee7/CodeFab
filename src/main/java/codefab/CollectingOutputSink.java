package codefab;

import codefab.core.OutputSink;

import java.util.List;

public final class CollectingOutputSink implements OutputSink {
    @Override
    public void print(String line) {
        throw new UnsupportedOperationException("print not implemented");
    }

    public List<String> lines() {
        throw new UnsupportedOperationException("lines not implemented");
    }

    public void clear() {
        throw new UnsupportedOperationException("clear not implemented");
    }
}
