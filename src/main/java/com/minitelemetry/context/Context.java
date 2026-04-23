package com.minitelemetry.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 不可变的上下文容器。
 *
 * <p>上下文中的条目通过 {@link ContextKey} 进行存取；调用 {@link #with(ContextKey, Object)}
 * 不会修改当前实例，而是返回一个包含新增条目的新上下文。</p>
 */
public final class Context {
    private static final Context ROOT = new Context(Collections.emptyMap());
    private static final ContextStorage STORAGE = new ThreadLocalContextStorage();

    private final Map<ContextKey<?>, Object> entries;

    private Context(Map<ContextKey<?>, Object> entries) {
        this.entries = Collections.unmodifiableMap(new HashMap<>(entries));
    }

    /**
     * 返回不包含任何条目的根上下文。
     *
     * @return 根上下文
     */
    public static Context root() {
        return ROOT;
    }

    /**
     * 返回当前线程正在使用的上下文。
     *
     * @return 当前线程的活动上下文
     */
    public static Context current() {
        return STORAGE.current();
    }

    /**
     * 清除当前线程已绑定的上下文，使其回退到根上下文。
     */
    public static void remove() {
        STORAGE.clear();
    }

    /**
     * 基于当前上下文创建一个包含新键值对的上下文副本。
     *
     * @param key 条目键
     * @param value 条目值
     * @param <T> 条目值类型
     * @return 新的上下文实例
     */
    public <T> Context with(ContextKey<T> key, T value) {
        Objects.requireNonNull(key, "key");

        Map<ContextKey<?>, Object> newEntries = new HashMap<ContextKey<?>, Object>(entries);
        newEntries.put(key, value);
        return new Context(newEntries);
    }

    /**
     * 从当前上下文读取指定键对应的值。
     *
     * @param key 条目键
     * @param <T> 条目值类型
     * @return 对应键的值；如果不存在则返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        return (T) entries.get(key);
    }

    /**
     * 将当前上下文绑定到当前线程，并返回用于恢复旧上下文的作用域句柄。
     *
     * @return 可关闭的作用域句柄
     */
    public Scope makeCurrent() {
        return STORAGE.attach(this);
    }

    /**
     * 捕获当前上下文，并返回一个在执行前自动恢复该上下文的 {@link Runnable} 包装器。
     *
     * @param runnable 原始任务
     * @return 带上下文恢复逻辑的任务包装器
     */
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

    /**
     * 捕获当前上下文，并返回一个在执行前自动恢复该上下文的 {@link Callable} 包装器。
     *
     * @param callable 原始任务
     * @param <V> 返回值类型
     * @return 带上下文恢复逻辑的任务包装器
     */
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
