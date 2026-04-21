package com.minitelemetry.trace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import com.minitelemetry.context.Context;
import com.minitelemetry.context.Scope;
import com.minitelemetry.exporter.SpanExporter;

public final class Span implements ReadableSpan {
    private final Tracer tracer;
    private final String name;
    private final SpanKind kind;
    private final SpanContext spanContext;
    private final String parentSpanId;
    private final long startEpochMillis;
    private final String threadName;
    private final Map<String, Object> attributes = Collections.synchronizedMap(new LinkedHashMap<String, Object>());
    private final AtomicBoolean ended = new AtomicBoolean(false);

    private volatile long endEpochMillis;
    private volatile StatusCode statusCode = StatusCode.UNSET;
    private volatile String statusMessage = "";

    Span(
            Tracer tracer,
            String name,
            SpanKind kind,
            SpanContext spanContext,
            String parentSpanId,
            long startEpochMillis,
            String threadName
    ) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.spanContext = Objects.requireNonNull(spanContext, "spanContext");
        this.parentSpanId = parentSpanId;
        this.startEpochMillis = startEpochMillis;
        this.threadName = Objects.requireNonNull(threadName, "threadName");
    }

    public static Span current() {
        return Context.current().span();
    }

    public Scope makeCurrent() {
        return Context.current().with(this).makeCurrent();
    }

    public Span setAttribute(String key, String value) {
        attributes.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    public Span setAttribute(String key, long value) {
        attributes.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    public Span setAttribute(String key, boolean value) {
        attributes.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    public Span setStatus(StatusCode statusCode, String message) {
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode");
        this.statusMessage = message == null ? "" : message;
        return this;
    }

    public void recordException(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        setStatus(StatusCode.ERROR, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        setAttribute("exception.type", throwable.getClass().getName());
        setAttribute("exception.message", String.valueOf(throwable.getMessage()));
    }

    public void end() {
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        endEpochMillis = System.currentTimeMillis();
        tracer.getExporter().export(this);
    }

    @Override
    public String getTracerName() {
        return tracer.getName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SpanKind getKind() {
        return kind;
    }

    @Override
    public SpanContext getSpanContext() {
        return spanContext;
    }

    @Override
    public String getParentSpanId() {
        return parentSpanId;
    }

    @Override
    public long getStartEpochMillis() {
        return startEpochMillis;
    }

    @Override
    public long getEndEpochMillis() {
        return endEpochMillis;
    }

    @Override
    public Map<String, Object> getAttributes() {
        synchronized (attributes) {
            return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
        }
    }

    @Override
    public StatusCode getStatusCode() {
        return statusCode;
    }

    @Override
    public String getStatusMessage() {
        return statusMessage;
    }

    @Override
    public String getThreadName() {
        return threadName;
    }
}
