package com.minitelemetry.sdk.trace;

import com.minitelemetry.sdk.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * 在当前线程寄存 span 与它的 {@link Scope},供开始与结束分处两个回调、无法用
 * try-with-resources 包住执行区间的埋点使用,典型如 {@code ChannelInterceptor}。
 *
 * <p>调用方必须保证每个 {@link #open} 与 {@link #close} 配对,且两者在同一线程上执行。
 * 支持嵌套,按后进先出关闭。两个方法都不抛出异常,埋点自身的失败不会传播给调用方。
 *
 * <p>线程安全:每个线程持有独立的栈。每个埋点位应持有独立的实例。
 */
public final class DeferredScope {

    private static final Logger log = LoggerFactory.getLogger(DeferredScope.class);

    /** 待关闭的作用域,按线程分栈保存。 */
    private final ThreadLocal<Deque<SpanAndScope>> holder = new ThreadLocal<>();

    /** 每个埋点点位建一个实例,彼此不共享栈。 */
    public DeferredScope() {
    }

    /**
     * 开启一个 span,将其挂到当前线程,并压栈等待 {@link #close}。
     *
     * <p>本方法不抛出异常。{@code starter} 为 {@code null}、返回 {@code null} 或抛出异常时
     * 放弃本次埋点,但仍会压入占位层以维持配对。
     */
    public Span open(Supplier<Span> starter) {
        Span span = null;
        Scope scope = null;
        try {
            span = starter.get();
            if (span != null) {
                scope = span.makeCurrent();
            }
        } catch (Throwable t) {
            log.debug("deferred scope open failed, instrumentation skipped for this call", t);
            // 已经建了一半就收尾,避免留下永不 end 的 span
            if (span != null) {
                closeScopeQuietly(scope, span);
                endQuietly(span);
            }
            span = null;
            scope = null;
        }
        push(span == null ? SpanAndScope.SKIPPED : new SpanAndScope(span, scope));
        return span;
    }

    /** 结束最近一次 {@link #open} 开启的 span,并还原上一层上下文。栈为空时无操作。 */
    public void close(Throwable error) {
        SpanAndScope opened = pop();
        if (opened == null || opened.span() == null) {
            return;
        }
        Span span = opened.span();

        try {
            if (error != null) {
                span.recordException(error);
            } else if (span.getStatusCode() == StatusCode.UNSET) {
                span.setStatus(StatusCode.OK);
            }
        } catch (Throwable t) {
            log.debug("span status update failed", t);
        }

        if (Span.current() != span) {
            log.debug("closing a scope that is no longer on top; some scope was left unclosed");
        }

        closeScopeQuietly(opened.scope(), span);
        endQuietly(span);
    }

    /** 返回本实例最近一次 open 且尚未 close 的 span。 */
    public Span current() {
        Deque<SpanAndScope> deque = holder.get();
        if (deque == null) {
            return null;
        }
        SpanAndScope opened = deque.peek();
        return opened == null ? null : opened.span();
    }

    /** 返回当前线程未闭合的层数。 */
    public int depth() {
        Deque<SpanAndScope> deque = holder.get();
        return deque == null ? 0 : deque.size();
    }

    private void push(SpanAndScope opened) {
        Deque<SpanAndScope> deque = holder.get();
        if (deque == null) {
            deque = new ArrayDeque<>(4);
            holder.set(deque);
        }
        deque.push(opened);
    }

    private SpanAndScope pop() {
        Deque<SpanAndScope> deque = holder.get();
        if (deque == null) {
            return null;
        }
        SpanAndScope opened = deque.poll();
        if (deque.isEmpty()) {
            holder.remove();
        }
        return opened;
    }

    private static void closeScopeQuietly(Scope scope, Span span) {
        if (scope == null) {
            return;
        }
        try {
            scope.close();
        } catch (Throwable t) {
            log.debug("scope close failed, span={}", span.getName(), t);
        }
    }

    private static void endQuietly(Span span) {
        try {
            span.end();
        } catch (Throwable t) {
            log.debug("span end failed, span={}", span.getName(), t);
        }
    }

    /** 一层未闭合的作用域所持有的 span 与作用域句柄。 */
    private record SpanAndScope(Span span, Scope scope) {
        static final SpanAndScope SKIPPED = new SpanAndScope(null, null);
    }
}
