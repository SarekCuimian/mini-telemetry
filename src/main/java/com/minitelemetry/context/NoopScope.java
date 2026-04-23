package com.minitelemetry.context;

/**
 * 空作用域实现。
 *
 * <p>用于本次上下文激活未发生任何状态变化的场景，{@link #close()} 不执行任何逻辑。</p>
 */
final class NoopScope implements Scope {
    static final NoopScope INSTANCE = new NoopScope();

    private NoopScope() {
    }

    @Override
    public void close() {
        // no-op
    }
}
