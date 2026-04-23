package com.minitelemetry.context;

import java.util.Objects;

/**
 * {@link Context} 中单个条目的类型化键。
 *
 * <p>当前实现按对象身份区分键，而不是按名称判等，因此同一个键应定义为常量并重复使用。</p>
 *
 * @param <T> 该键对应值的类型
 */
public final class ContextKey<T> {
    private final String name;

    private ContextKey(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * 创建一个新的上下文键。
     *
     * @param name 键的可读名称，主要用于调试与日志输出
     * @param <T> 键对应值的类型
     * @return 新的上下文键
     */
    public static <T> ContextKey<T> create(String name) {
        return new ContextKey<T>(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
