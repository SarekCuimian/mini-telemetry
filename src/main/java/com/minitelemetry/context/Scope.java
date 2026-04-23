package com.minitelemetry.context;

/**
 * 表示一次已激活上下文的作用域句柄。
 *
 * <p>调用方通常通过 {@code try-with-resources} 使用该接口，在离开作用域时自动恢复
 * 先前绑定到当前线程的上下文。</p>
 */
public interface Scope extends AutoCloseable {
    @Override
    void close();

    /**
     * 返回一个不执行任何恢复逻辑的空作用域。
     *
     * <p>当本次操作未发生实际上下文切换时使用，例如重复激活同一个上下文。</p>
     *
     * @return 空作用域实现
     */
    static Scope noop() {
        return NoopScope.INSTANCE;
    }
}
