package com.minitelemetry.trace;

import java.util.Objects;
import com.minitelemetry.context.Context;

public final class SpanBuilder {
    private final Tracer tracer;
    private final String spanName;
    private Context explicitParent;
    private SpanKind spanKind = SpanKind.INTERNAL;

    SpanBuilder(Tracer tracer, String spanName) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.spanName = Objects.requireNonNull(spanName, "spanName");
    }

    public SpanBuilder setParent(Context parent) {
        this.explicitParent = parent;
        return this;
    }

    public SpanBuilder setSpanKind(SpanKind spanKind) {
        this.spanKind = Objects.requireNonNull(spanKind, "spanKind");
        return this;
    }

    public Span startSpan() {
        Context parentContext = explicitParent != null ? explicitParent : Context.current();
        Span parentSpan = parentContext.span();

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
