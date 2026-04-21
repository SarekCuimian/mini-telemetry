package com.minitelemetry.trace;

import java.util.Objects;

public final class SpanContext {
    private final String traceId;
    private final String spanId;

    public SpanContext(String traceId, String spanId) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.spanId = Objects.requireNonNull(spanId, "spanId");
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }
}
