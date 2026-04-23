package com.minitelemetry.trace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import com.minitelemetry.context.Context;
import com.minitelemetry.context.ContextKey;
import com.minitelemetry.context.Scope;

/**
 * 单个 Trace 节点的最小实现。
 *
 * <p>{@link Span} 负责维护自身的基础元数据、状态、属性以及结束时的导出行为。</p>
 */
public final class Span implements ReadableSpan {
    private static final ContextKey<Span> KEY = ContextKey.create("current-span");

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

    /**
     * 返回当前线程活动上下文中的 Span。
     *
     * @return 当前线程的活动 Span；如果当前上下文中不存在 Span，则返回 {@code null}
     */
    public static Span current() {
        return fromContext(Context.current());
    }

    /**
     * 从给定上下文中读取 Span。
     *
     * @param context 要读取的上下文
     * @return 上下文中的 Span；如果不存在则返回 {@code null}
     */
    public static Span fromContext(Context context) {
        return context == null ? null : context.get(KEY);
    }

    /**
     * 将当前 Span 存入给定上下文，并返回新的上下文实例。
     *
     * @param context 原始上下文
     * @return 包含当前 Span 的新上下文
     */
    public Context storeInContext(Context context) {
        Objects.requireNonNull(context, "context");
        return context.with(KEY, this);
    }

    /**
     * 将当前 Span 绑定到当前线程，并返回用于恢复旧上下文的作用域句柄。
     *
     * @return 可关闭的作用域句柄
     */
    public Scope makeCurrent() {
        return storeInContext(Context.current()).makeCurrent();
    }

    /**
     * 设置字符串属性。
     *
     * @param key 属性名
     * @param value 属性值
     * @return 当前 Span
     */
    public Span setAttribute(String key, String value) {
        attributes.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    /**
     * 设置长整型属性。
     *
     * @param key 属性名
     * @param value 属性值
     * @return 当前 Span
     */
    public Span setAttribute(String key, long value) {
        attributes.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    /**
     * 设置布尔属性。
     *
     * @param key 属性名
     * @param value 属性值
     * @return 当前 Span
     */
    public Span setAttribute(String key, boolean value) {
        attributes.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    /**
     * 设置 Span 状态。
     *
     * @param statusCode 状态码
     * @param message 状态消息，可为 {@code null}
     * @return 当前 Span
     */
    public Span setStatus(StatusCode statusCode, String message) {
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode");
        this.statusMessage = message == null ? "" : message;
        return this;
    }

    /**
     * 记录异常，并将 Span 状态标记为 {@link StatusCode#ERROR}。
     *
     * @param throwable 要记录的异常；如果为 {@code null} 则忽略
     */
    public void recordException(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        setStatus(StatusCode.ERROR, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        setAttribute("exception.type", throwable.getClass().getName());
        setAttribute("exception.message", String.valueOf(throwable.getMessage()));
    }

    /**
     * 结束当前 Span 并触发导出。
     *
     * <p>该方法是幂等的；多次调用只会在第一次真正结束并导出。</p>
     */
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
