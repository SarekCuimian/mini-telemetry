package com.minitelemetry.sdk.trace;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.trace.sampling.SamplingResult;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link Span} 构造器,通过 {@link Tracer#spanBuilder(String)} 获取。
 * <p>非线程安全,仅供单一调用栈链式使用。{@link #startSpan()} 是终止操作。
 * 内部按三分支(root/remote parent/local child)决定父子关系与 trace 级 sampled/recording 决策。
 */
public final class SpanBuilder {

    private final String spanName;
    private Context explicitParentContext;
    private SpanKind spanKind = SpanKind.INTERNAL;
    /** null=未声明;true=强制 local root;false=强制非 local root。 */
    private Boolean explicitLocalRoot;

    SpanBuilder(String spanName) {
        this.spanName = Objects.requireNonNull(spanName, "spanName");
    }

    public SpanBuilder setParent(Context parent) {
        this.explicitParentContext = parent;
        return this;
    }

    public SpanBuilder setSpanKind(SpanKind spanKind) {
        this.spanKind = Objects.requireNonNull(spanKind, "spanKind");
        return this;
    }

    /** 显式声明本 span 为服务的 local root,覆盖 startSpan 中基于 parent 上下文的自动判定。 */
    public SpanBuilder markAsLocalRoot() {
        this.explicitLocalRoot = true;
        return this;
    }

    public Span startSpan() {
        Context parentContext = explicitParentContext != null ? explicitParentContext : Context.current();
        Span parentSpan = Span.fromContext(parentContext);

        String traceId;
        String parentSpanId;
        AtomicBoolean sampledHolder;
        String localRootSpanName;
        boolean recording;
        boolean localRoot;

        if (parentSpan == null) {
            // root:本进程发起的新 trace
            traceId = IdGenerator.generateTraceId();
            parentSpanId = null;
            localRootSpanName = spanName;
            localRoot = true;

            SamplingResult r = TracerRuntime.getSampler()
                    .shouldSample(traceId, spanName, false, false);
            sampledHolder = new AtomicBoolean(r.isSampled());
            recording = r.isRecording();
        } else if (parentSpan.getSpanContext().isRemote()) {
            // remote parent(extract 产物的占位 Span):沿用上游 traceId 与 sampled 决策
            SpanContext remote = parentSpan.getSpanContext();
            traceId = remote.getTraceId();
            parentSpanId = remote.getSpanId();
            localRootSpanName = spanName;
            localRoot = true;
            sampledHolder = remote.sampledHolder();

            SamplingResult r = TracerRuntime.getSampler()
                    .shouldSample(traceId, spanName, true, remote.isSampled());
            recording = r.isRecording();
        } else {
            // 子 span 完全沿袭 parent 的 trace 级决策,不再调 Sampler
            SpanContext parentSpanContext = parentSpan.getSpanContext();
            traceId = parentSpanContext.getTraceId();
            parentSpanId = parentSpanContext.getSpanId();
            localRootSpanName = parentSpan.getLocalRootSpanName();
            localRoot = false;
            sampledHolder = parentSpanContext.sampledHolder();
            recording = parentSpan.isRecording();
        }

        if (explicitLocalRoot != null) {
            localRoot = explicitLocalRoot;
        }

        SpanContext spanContext = new SpanContext(traceId, IdGenerator.generateSpanId(), sampledHolder);

        return new Span(
                TracerRuntime.getProcessors(),
                spanName,
                localRootSpanName,
                spanKind,
                spanContext,
                parentSpanId,
                System.currentTimeMillis(),
                Thread.currentThread().getName(),
                recording,
                localRoot
        );
    }
}
