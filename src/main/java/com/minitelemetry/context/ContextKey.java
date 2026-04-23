package com.minitelemetry.context;

import java.util.Objects;

public final class ContextKey<T> {
    private final String name;

    private ContextKey(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public static <T> ContextKey<T> create(String name) {
        return new ContextKey<T>(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
