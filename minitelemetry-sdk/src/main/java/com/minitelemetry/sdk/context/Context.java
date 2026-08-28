package com.minitelemetry.sdk.context;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 不可变的键值上下文,基于 ThreadLocal 在进程内传播。
 *
 * <p>{@link #with(ContextKey, Object)} 返回新副本;{@link #makeCurrent()} 把自己挂到当前线程,
 * 配合 try-with-resources 自动还原。{@link #wrap} 用于跨线程传播(线程池/异步)。
 *
 * <p>实现上参考 OpenTelemetry Java 的小数组结构,按 {@code key,value,key,value} 存储。
 * Context 通常只需少量条目,数组复制比 HashMap + Map.copyOf 更轻。
 */
public final class Context {
    private static final Context ROOT = new Context(new Object[0]);
    private static final ContextStorage STORAGE = new ContextStorage();

    private final Object[] entries;

    private Context(Object[] entries) {
        this.entries = entries;
    }

    public static Context root() {
        return ROOT;
    }

    public static Context current() {
        Context context = STORAGE.current();
        return context == null ? ROOT : context;
    }

    /** 清空当前线程的 Context,线程池复用时务必调用避免泄漏。 */
    public static void remove() {
        STORAGE.clear();
    }

    public static void setContextListener(ContextListener listener) {
        ContextStorage.setListener(listener);
    }

    public <T> Context with(ContextKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        for (int i = 0; i < entries.length; i += 2) {
            if (entries[i] == key) {
                if (entries[i + 1] == value) {
                    return this;
                }
                Object[] newEntries = entries.clone();
                newEntries[i + 1] = value;
                return new Context(newEntries);
            }
        }

        Object[] newEntries = Arrays.copyOf(entries, entries.length + 2);
        newEntries[newEntries.length - 2] = key;
        newEntries[newEntries.length - 1] = value;
        return new Context(newEntries);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        for (int i = 0; i < entries.length; i += 2) {
            if (entries[i] == key) {
                return (T) entries[i + 1];
            }
        }
        return null;
    }

    public Scope makeCurrent() {
        return STORAGE.attach(this);
    }

    /** 把 Runnable 包成“在本 Context 下执行”,用于跨线程传播。 */
    public Runnable wrap(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        Context captured = this;
        return () -> {
            try (Scope ignored = captured.makeCurrent()) {
                runnable.run();
            }
        };
    }

    public <V> Callable<V> wrap(Callable<V> callable) {
        Objects.requireNonNull(callable, "callable");
        Context captured = this;
        return () -> {
            try (Scope ignored = captured.makeCurrent()) {
                return callable.call();
            }
        };
    }
}
