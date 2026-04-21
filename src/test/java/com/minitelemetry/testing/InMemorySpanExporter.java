package com.minitelemetry.testing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.minitelemetry.exporter.SpanExporter;
import com.minitelemetry.trace.ReadableSpan;

final class InMemorySpanExporter implements SpanExporter {
    private final List<ReadableSpan> spans = new CopyOnWriteArrayList<ReadableSpan>();

    @Override
    public void export(ReadableSpan span) {
        spans.add(span);
    }

    List<ReadableSpan> getSpans() {
        return spans;
    }
}
