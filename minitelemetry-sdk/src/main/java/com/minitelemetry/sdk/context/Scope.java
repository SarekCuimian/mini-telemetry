package com.minitelemetry.sdk.context;

/**
 * {@link Context} 在当前线程的活动作用域,由 {@link ContextStorage#attach(Context)} 创建。
 * <p>非线程安全,仅供创建它的线程在 try-with-resources 中关闭。
 *
 * <pre>{@code
 * try (Scope ignored = span.makeCurrent()) {
 *     // 此处 Span.current() 返回上面的 span
 * }
 * }</pre>
 */
public final class Scope implements AutoCloseable {
    /** 不做任何还原的单例,供 attach 幂等路径返回。 */
    private static final Scope NOOP = new Scope();

    private final ContextStorage storage;
    private final Context beforeAttach;
    private final Context toAttach;
    private boolean closed;

    private Scope() {
        this.storage = null;
        this.beforeAttach = null;
        this.toAttach = null;
        this.closed = true;
    }

    Scope(ContextStorage storage, Context beforeAttach, Context toAttach) {
        this.storage = storage;
        this.beforeAttach = beforeAttach;
        this.toAttach = toAttach;
    }

    static Scope noop() {
        return NOOP;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (storage != null) storage.restore(beforeAttach, toAttach);
    }
}
