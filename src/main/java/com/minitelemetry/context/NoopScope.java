package com.minitelemetry.context;

final class NoopScope implements Scope {
    static final NoopScope INSTANCE = new NoopScope();

    private NoopScope() {
    }

    @Override
    public void close() {
        // no-op
    }
}
