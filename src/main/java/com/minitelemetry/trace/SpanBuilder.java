package com.minitelemetry.trace;

import java.util.Objects;
import com.minitelemetry.context.Context;

/**
 * 用于构建并启动 {@link Span} 的构建器。
 */
public final class SpanBuilder {
    private final Tracer tracer;
    private final String spanName;
    private Context explicitParent;
    private SpanKind spanKind = SpanKind.INTERNAL;

    SpanBuilder(Tracer tracer, String spanName) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.spanName = Objects.requireNonNull(spanName, "spanName");
    }

    /**
     * 显式指定父上下文。
     *
     * <p>一旦设置，后续 {@link #startSpan()} 将优先使用该上下文中的 Span 作为父 Span，
     * 而不是当前线程中的活动上下文。</p>
     *
     * @param parent 父上下文
     * @return 当前构建器
     */
    public SpanBuilder setParent(Context parent) {
        this.explicitParent = parent;
        return this;
    }

    /**
     * 设置 Span 类型。
     *
     * @param spanKind Span 类型
     * @return 当前构建器
     */
    public SpanBuilder setSpanKind(SpanKind spanKind) {
        this.spanKind = Objects.requireNonNull(spanKind, "spanKind");
        return this;
    }

    /**
     * 启动一个新的 Span。
     *
     * <p>如果存在父 Span，则复用父 Span 的 traceId 并记录 parentSpanId；
     * 否则创建新的 traceId，生成根 Span。</p>
     *
     * @return 新创建的 Span
     */
    public Span startSpan() {
        Context parentContext = explicitParent != null ? explicitParent : Context.current();
        Span parentSpan = Span.fromContext(parentContext);

        String traceId = parentSpan == null
                ? IdGenerator.newTraceId()
                : parentSpan.getSpanContext().getTraceId();

        String parentSpanId = parentSpan == null
                ? null
                : parentSpan.getSpanContext().getSpanId();

        return new Span(
                tracer,
                spanName,
                spanKind,
                new SpanContext(traceId, IdGenerator.newSpanId()),
                parentSpanId,
                System.currentTimeMillis(),
                Thread.currentThread().getName()
        );
    }
}
