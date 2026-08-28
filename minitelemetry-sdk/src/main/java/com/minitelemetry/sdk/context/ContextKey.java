package com.minitelemetry.sdk.context;

import java.util.Objects;

/**
 * {@link Context} 条目的类型化键。
 *
 * @param <T> 该键对应值的类型
 */
public final class ContextKey<T> {
    private final String name;

    private ContextKey(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public static <T> ContextKey<T> create(String name) {
        return new ContextKey<>(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
