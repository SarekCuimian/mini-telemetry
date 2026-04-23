package com.minitelemetry.context;

/**
 * 基于 {@link ThreadLocal} 的上下文存储实现。
 */
final class ThreadLocalContextStorage implements ContextStorage {
    private final ThreadLocal<Context> local = ThreadLocal.withInitial(Context::root);

    @Override
    public Context current() {
        Context context = local.get();
        return context == null ? Context.root() : context;
    }

    @Override
    public Scope attach(Context toAttach) {
        if (toAttach == null) {
            return Scope.noop();
        }

        Context beforeAttach = current();
        if (beforeAttach == toAttach) {
            return Scope.noop();
        }

        local.set(toAttach);
        return new ScopeImpl(this, beforeAttach, toAttach);
    }

    @Override
    public void clear() {
        local.remove();
    }

    /**
     * 恢复作用域激活前的上下文。
     *
     * <p>只有当前线程中仍然绑定着本次作用域安装的上下文时才执行恢复，以避免乱序关闭
     * 不同作用域时破坏线程中的上下文状态。</p>
     */
    private void restore(Context beforeAttach, Context toAttach) {
        Context current = current();
        if (current == toAttach) {
            local.set(beforeAttach);
        }
    }

    /**
     * 与一次 {@link #attach(Context)} 调用对应的恢复句柄。
     */
    private static final class ScopeImpl implements Scope {
        private final ThreadLocalContextStorage storage;
        private final Context beforeAttach;
        private final Context toAttach;
        private boolean closed;

        private ScopeImpl(ThreadLocalContextStorage storage, Context beforeAttach, Context toAttach) {
            this.storage = storage;
            this.beforeAttach = beforeAttach;
            this.toAttach = toAttach;
        }

        @Override
        public void close() {
            // close() 允许被重复调用，但真正的恢复逻辑只执行一次。
            if (closed) {
                return;
            }
            closed = true;
            storage.restore(beforeAttach, toAttach);
        }
    }
}
