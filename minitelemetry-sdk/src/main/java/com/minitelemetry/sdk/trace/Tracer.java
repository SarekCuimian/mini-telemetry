package com.minitelemetry.sdk.trace;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.context.Scope;
import com.minitelemetry.sdk.propagation.TextMapGetter;
import com.minitelemetry.sdk.propagation.TextMapSetter;
import com.minitelemetry.sdk.propagation.W3CTraceContextPropagator;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * SDK 业务入口。所有 {@link Span} 均通过 {@link #spanBuilder(String)} 或 {@link #withSpan(String, Action)} 创建。
 *
 * <p>无状态。全局 sampler / processor 由 {@link TracerRuntime} 持有,
 * 在 {@link TracerRuntime#install} 之前创建的 span 会被静默 drop。
 */
public final class Tracer {

    private Tracer() {
    }

    /** @param spanName 操作名,建议为动词短句(如 {@code "createOrder"}) */
    public static SpanBuilder spanBuilder(String spanName) {
        Objects.requireNonNull(spanName, "spanName");
        return new SpanBuilder(spanName);
    }

    public static void withSpan(String spanName, Action action) {
        Objects.requireNonNull(action, "action");
        withSpan(spanName, SpanKind.INTERNAL, action);
    }

    public static void withSpan(String spanName, SpanKind kind, Action action) {
        Objects.requireNonNull(action, "action");
        executeWithSpan(spanName, kind, () -> {
            action.execute();
            return null;
        });
    }

    public static <T> T withSpan(String spanName, ReturningAction<T> action) {
        Objects.requireNonNull(action, "action");
        return withSpan(spanName, SpanKind.INTERNAL, action);
    }

    public static <T> T withSpan(String spanName, SpanKind kind, ReturningAction<T> action) {
        Objects.requireNonNull(action, "action");
        return executeWithSpan(spanName, kind, action);
    }

    public static <E extends Throwable> void withCheckedSpan(
            String spanName, CheckedAction<E> action) throws E {
        Objects.requireNonNull(action, "action");
        withCheckedSpan(spanName, SpanKind.INTERNAL, action);
    }

    public static <E extends Throwable> void withCheckedSpan(
            String spanName, SpanKind kind, CheckedAction<E> action) throws E {
        Objects.requireNonNull(action, "action");
        executeWithCheckedSpan(spanName, kind, () -> {
            action.execute();
            return null;
        });
    }

    public static <T, E extends Throwable> T withCheckedSpan(
            String spanName, CheckedReturningAction<T, E> action) throws E {
        Objects.requireNonNull(action, "action");
        return withCheckedSpan(spanName, SpanKind.INTERNAL, action);
    }

    public static <T, E extends Throwable> T withCheckedSpan(
            String spanName, SpanKind kind, CheckedReturningAction<T, E> action) throws E {
        Objects.requireNonNull(action, "action");
        return executeWithCheckedSpan(spanName, kind, action);
    }

    /** 捕获当前 {@link Context},返回一个后续执行时会恢复该 Context 的 {@link Runnable}。 */
    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task");
        return Context.current().wrap(task);
    }

    /** 捕获当前 {@link Context},返回一个后续执行时会恢复该 Context 的 {@link Callable}。 */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return Context.current().wrap(task);
    }

    /** 把当前 trace 上下文写入出站 header({@code traceparent}),不创建 CLIENT span。 */
    public static void inject(Map<String, String> headers) {
        inject(headers, (Map<String, String> carrier, String key, String value) -> {
            if (carrier != null) {
                carrier.put(key, value);
            }
        });
    }

    /** 把当前 trace 上下文写入任意出站载体,不创建 CLIENT span。 */
    public static <C> void inject(C carrier, TextMapSetter<C> setter) {
        W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, setter);
    }

    /** 从入站 header 还原上游上下文。基底固定 Context.root()。 */
    public static Context extract(Map<String, String> headers) {
        return extract(headers,
                (Map<String, String> carrier, String key) -> carrier == null ? null : carrier.get(key));
    }

    /** 从任意入站载体还原上游上下文。基底固定 Context.root()。 */
    public static <C> Context extract(C carrier, TextMapGetter<C> getter) {
        return W3CTraceContextPropagator.getInstance().extract(Context.root(), carrier, getter);
    }

    private static <T> T executeWithSpan(String spanName, SpanKind kind, ReturningAction<T> action) {
        Objects.requireNonNull(action, "action");
        Span span = spanBuilder(spanName)
                .setSpanKind(kind)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            T result = action.execute();
            if (span.getStatusCode() == StatusCode.UNSET) {
                span.setStatus(StatusCode.OK);
            }
            return result;
        } catch (RuntimeException | Error e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private static <T, E extends Throwable> T executeWithCheckedSpan(
            String spanName,
            SpanKind kind,
            CheckedReturningAction<T, E> action
    ) throws E {
        Objects.requireNonNull(action, "action");
        Span span = spanBuilder(spanName)
                .setSpanKind(kind)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            T result = action.execute();
            if (span.getStatusCode() == StatusCode.UNSET) {
                span.setStatus(StatusCode.OK);
            }
            return result;
        } catch (Throwable e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    public static void setSpanAttribute(String key, String value) {
        Span span = Span.current();
        if (span != null) span.setAttribute(key, value);
    }

    public static void setSpanAttribute(String key, long value) {
        Span span = Span.current();
        if (span != null) span.setAttribute(key, value);
    }

    public static void setSpanAttribute(String key, boolean value) {
        Span span = Span.current();
        if (span != null) span.setAttribute(key, value);
    }

    public static void setSpanAttribute(String key, double value) {
        Span span = Span.current();
        if (span != null) span.setAttribute(key, value);
    }

    public static void setSpanStatus(StatusCode statusCode) {
        Span span = Span.current();
        if (span != null) span.setStatus(statusCode);
    }

    public static void setSpanStatus(StatusCode statusCode, String message) {
        Span span = Span.current();
        if (span != null) span.setStatus(statusCode, message);
    }

    /** 获取当前 TraceId;当前无 Span 时返回空字符串。 */
    public static String currentTraceId() {
        Span span = Span.current();
        return span == null ? "" : span.getSpanContext().getTraceId();
    }

    /** 获取当前 SpanId;当前无 Span 时返回空字符串。 */
    public static String currentSpanId() {
        Span span = Span.current();
        return span == null ? "" : span.getSpanContext().getSpanId();
    }

    @FunctionalInterface
    public interface Action {
        void execute();
    }

    @FunctionalInterface
    public interface ReturningAction<T> {
        T execute();
    }

    @FunctionalInterface
    public interface CheckedAction<E extends Throwable> {
        void execute() throws E;
    }

    @FunctionalInterface
    public interface CheckedReturningAction<T, E extends Throwable> {
        T execute() throws E;
    }
}
