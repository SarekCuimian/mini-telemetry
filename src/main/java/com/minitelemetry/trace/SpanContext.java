package com.minitelemetry.trace;

import java.util.Objects;

/**
 * 标识单个 Span 的上下文信息。
 */
public final class SpanContext {
    private final String traceId;
    private final String spanId;

    /**
     * 创建一个新的 Span 上下文。
     *
     * @param traceId Trace 标识
     * @param spanId Span 标识
     */
    public SpanContext(String traceId, String spanId) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.spanId = Objects.requireNonNull(spanId, "spanId");
    }

    /**
     * 返回所属 Trace 的标识。
     *
     * @return traceId
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * 返回当前 Span 的标识。
     *
     * @return spanId
     */
    public String getSpanId() {
        return spanId;
    }
}
