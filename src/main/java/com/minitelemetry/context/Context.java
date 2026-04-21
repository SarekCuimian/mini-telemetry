package com.minitelemetry.context;

import java.util.Objects;
import java.util.concurrent.Callable;
import com.minitelemetry.trace.Span;

public final class Context {
    private static final Context ROOT = new Context(null);
    private static final ContextStorage STORAGE = new ThreadLocalContextStorage();

    private final Span span;

    private Context(Span span) {
        this.span = span;
    }

    public static Context root() {
        return ROOT;
    }

    public static Context current() {
        return STORAGE.current();
    }

    public Span span() {
        return span;
    }

    public Context with(Span span) {
        return new Context(span);
    }

    public Scope makeCurrent() {
        return STORAGE.attach(this);
    }

    public Runnable wrap(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        Context captured = this;
        return new Runnable() {
            @Override
            public void run() {
                try (Scope ignored = captured.makeCurrent()) {
                    runnable.run();
                }
            }
        };
    }

    public <V> Callable<V> wrap(Callable<V> callable) {
        Objects.requireNonNull(callable, "callable");
        Context captured = this;
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                try (Scope ignored = captured.makeCurrent()) {
                    return callable.call();
                }
            }
        };
    }
}
