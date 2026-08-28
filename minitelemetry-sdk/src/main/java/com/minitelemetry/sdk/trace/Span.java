package com.minitelemetry.sdk.trace;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.context.ContextKey;
import com.minitelemetry.sdk.context.Scope;
import com.minitelemetry.sdk.trace.export.SpanProcessor;
import com.minitelemetry.sdk.trace.sampling.strategy.ErrorLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SDK 核心 span 实现,通过 {@link SpanBuilder#startSpan()} 创建。
 *
 * <p>线程安全:attributes 走 lazy 初始化 + synchronized map,end 走 CAS 幂等,status 等 volatile。
 * 高并发写入不阻塞业务线程,错误翻牌细节见 {@link #setStatus}。
 *
 * <p>{@code recording=false} 的 DROP span 仍会走完整生命周期以支撑 metric 聚合,
 * 但不会保存 attribute / exception detail / status message 等 trace 明细。
 */
public final class Span implements ReadableSpan {

    private static final Logger log = LoggerFactory.getLogger(Span.class);
    private static final ContextKey<Span> KEY = ContextKey.create("current-span");

    private final List<SpanProcessor> processors;
    private final String name;
    private final String localRootSpanName;
    private final SpanKind kind;
    private final SpanContext spanContext;
    private final String parentSpanId;
    private final long startEpochMillis;
    private final String threadName;
    private final boolean recording;
    private final boolean localRoot;
    private volatile Map<String, Object> attributes;
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private volatile long endEpochMillis;
    private volatile StatusCode statusCode = StatusCode.UNSET;
    private volatile String statusMessage = "";

    Span(List<SpanProcessor> processors,
         String name,
         String localRootSpanName,
         SpanKind kind,
         SpanContext spanContext,
         String parentSpanId,
         long startEpochMillis,
         String threadName,
         boolean recording,
         boolean localRoot) {
        this.processors = Objects.requireNonNull(processors, "processors");
        this.name = Objects.requireNonNull(name, "name");
        this.localRootSpanName = Objects.requireNonNull(localRootSpanName, "localRootSpanName");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.spanContext = Objects.requireNonNull(spanContext, "spanContext");
        this.parentSpanId = parentSpanId;
        this.startEpochMillis = startEpochMillis;
        this.threadName = Objects.requireNonNull(threadName, "threadName");
        this.recording = recording;
        this.localRoot = localRoot;
    }

    /** 把跨进程还原的 remote {@link SpanContext} 包装为占位 Span。 */
    public static Span wrap(SpanContext remoteContext) {
        Objects.requireNonNull(remoteContext, "remoteContext");
        if (!remoteContext.isRemote()) {
            throw new IllegalArgumentException("Span.wrap only accepts remote SpanContext");
        }
        return new Span(List.of(), "remote", "remote", SpanKind.INTERNAL,
                remoteContext, null, 0L, "", false, false);
    }

    public static Span current() {
        return fromContext(Context.current());
    }

    public static Span fromContext(Context context) {
        return context == null ? null : context.get(KEY);
    }

    public Scope makeCurrent() {
        return storeInContext(Context.current()).makeCurrent();
    }

    public Context storeInContext(Context context) {
        Objects.requireNonNull(context, "context");
        return context.with(KEY, this);
    }

    @Override
    public boolean isRecording() { return recording; }

    @Override
    public boolean isLocalRoot() { return localRoot; }

    public Span setAttribute(String key, String value) {
        Objects.requireNonNull(key, "key");
        if (!recording) return this;
        getOrCreateAttributes().put(key, value);
        return this;
    }

    public Span setAttribute(String key, long value) {
        Objects.requireNonNull(key, "key");
        if (!recording) return this;
        getOrCreateAttributes().put(key, value);
        return this;
    }

    public Span setAttribute(String key, boolean value) {
        Objects.requireNonNull(key, "key");
        if (!recording) return this;
        getOrCreateAttributes().put(key, value);
        return this;
    }

    public Span setAttribute(String key, double value) {
        Objects.requireNonNull(key, "key");
        if (!recording) return this;
        if (!Double.isFinite(value)) return this;
        getOrCreateAttributes().put(key, value);
        return this;
    }

    private Map<String, Object> getOrCreateAttributes() {
        Map<String, Object> map = attributes;
        if (map != null) return map;
        synchronized (this) {
            if (attributes == null) {
                attributes = Collections.synchronizedMap(new LinkedHashMap<>());
            }
            return attributes;
        }
    }

    public Span setStatus(StatusCode statusCode) {
        return setStatus(statusCode, "");
    }

    public Span setStatus(StatusCode statusCode, String message) {
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode");
        if (!recording) {
            this.statusMessage = "";
            return this;
        }
        this.statusMessage = statusCode == StatusCode.ERROR && message != null ? message : "";

        if (statusCode != StatusCode.ERROR || spanContext.isSampled()) return this;
        ErrorLimiter limiter = TracerRuntime.getErrorLimiter();
        if (limiter == null) return this;
        if (!limiter.tryConsume(localRootSpanName)) return this;
        if (!spanContext.markSampled()) {
            limiter.refund(localRootSpanName);
        }
        return this;
    }

    public void recordException(Throwable throwable) {
        if (throwable == null) return;
        setStatus(StatusCode.ERROR, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        setAttribute("exception.type", throwable.getClass().getName());
        setAttribute("exception.message", String.valueOf(throwable.getMessage()));
    }

    public void end() {
        if (!ended.compareAndSet(false, true)) return;
        endEpochMillis = System.currentTimeMillis();
        for (SpanProcessor p : processors) {
            try {
                p.onEnd(this);
            } catch (Throwable t) {
                log.error("onEnd failed, processor={}", p.getClass().getName(), t);
            }
        }
    }

    @Override public String getName() { return name; }
    @Override public String getLocalRootSpanName() { return localRootSpanName; }
    @Override public SpanKind getKind() { return kind; }
    @Override public SpanContext getSpanContext() { return spanContext; }
    @Override public String getParentSpanId() { return parentSpanId; }
    @Override public long getStartEpochMillis() { return startEpochMillis; }
    @Override public long getEndEpochMillis() { return endEpochMillis; }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> a = attributes;
        if (a == null) return Collections.emptyMap();
        synchronized (a) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(a));
        }
    }

    @Override public StatusCode getStatusCode() { return statusCode; }
    @Override public String getStatusMessage() { return statusMessage; }
    @Override public String getThreadName() { return threadName; }
}
