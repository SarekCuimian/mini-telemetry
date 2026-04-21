package com.minitelemetry.trace;

import java.util.Map;

public interface ReadableSpan {
    String getTracerName();

    String getName();

    SpanKind getKind();

    SpanContext getSpanContext();

    String getParentSpanId();

    long getStartEpochMillis();

    long getEndEpochMillis();

    Map<String, Object> getAttributes();

    StatusCode getStatusCode();

    String getStatusMessage();

    String getThreadName();
}
