package com.minitelemetry.exporter;

import com.minitelemetry.trace.ReadableSpan;

public interface SpanExporter {
    void export(ReadableSpan span);
}
