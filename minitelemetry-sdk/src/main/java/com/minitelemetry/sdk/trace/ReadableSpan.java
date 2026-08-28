package com.minitelemetry.sdk.trace;

import java.util.Map;

/** Span 的只读视图,供 {@code SpanProcessor} / {@code SpanExporter} 消费。 */
public interface ReadableSpan {
    String getName();
    SpanKind getKind();
    SpanContext getSpanContext();

    /**
     * 本 span 所属 trace 的入口 span 名,由 {@code SpanBuilder} 在创建时从 parent 沿袭。
     * root 时即等于 {@link #getName()}。用于 ErrorLimiter 按 root 维度扣预算、metrics 聚合分组。
     */
    String getLocalRootSpanName();

    boolean isRecording();
    String getParentSpanId();
    default boolean isRoot() { return getParentSpanId() == null; }

    /** 是否为<b>本进程子树</b>的根:真 root,或 remote parent 下的第一个本地 span。 */
    default boolean isLocalRoot() { return isRoot(); }

    long getStartEpochMillis();
    long getEndEpochMillis();
    Map<String, Object> getAttributes();
    StatusCode getStatusCode();
    String getStatusMessage();
    String getThreadName();
}
