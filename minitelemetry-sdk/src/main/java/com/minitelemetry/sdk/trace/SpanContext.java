package com.minitelemetry.sdk.trace;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Span 对外暴露的最小标识 + 采样位。
 * <p>同一 trace 的所有 SpanContext 共享同一个 {@link AtomicBoolean} sampled 实例
 * (root 创建时 new,子 span 复用 parent 引用),任意一处 {@code markSampled()}
 * 都让整 trace 立即翻为 sampled。翻牌单向 false -> true,永不回退。
 */
public final class SpanContext {
    private final String traceId;
    private final String spanId;
    private final AtomicBoolean sampled;
    private final boolean remote;

    public SpanContext(String traceId, String spanId, AtomicBoolean sampled) {
        this(traceId, spanId, sampled, false);
    }

    private SpanContext(String traceId, String spanId, AtomicBoolean sampled, boolean remote) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.spanId = Objects.requireNonNull(spanId, "spanId");
        this.sampled = Objects.requireNonNull(sampled, "sampled");
        this.remote = remote;
    }

    /** 便捷构造,内部新建独立 holder。仅用于测试 mock / HTTP 直接构造。 */
    public SpanContext(String traceId, String spanId, boolean sampled) {
        this(traceId, spanId, new AtomicBoolean(sampled), false);
    }

    public static SpanContext createRemote(String traceId, String spanId, boolean sampled) {
        return new SpanContext(traceId, spanId, new AtomicBoolean(sampled), true);
    }

    public boolean isRemote() { return remote; }
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public boolean isSampled() { return sampled.get(); }

    /** CAS 翻牌 false -> true。 */
    boolean markSampled() { return sampled.compareAndSet(false, true); }

    /** 内部 holder 引用,仅供同 trace 子 span 创建时复用。 */
    AtomicBoolean sampledHolder() { return sampled; }
}
