package com.minitelemetry.context;

public interface Scope extends AutoCloseable {
    @Override
    void close();

    static Scope noop() {
        return NoopScope.INSTANCE;
    }
}
