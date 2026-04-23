package com.minitelemetry.trace;

import java.util.Map;

/**
 * 已结束 Span 的只读视图。
 *
 * <p>导出器只依赖该接口，而不直接依赖具体的 {@link Span} 实现。</p>
 */
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
