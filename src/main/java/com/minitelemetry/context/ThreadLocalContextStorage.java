package com.minitelemetry.context;

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

    private void restore(Context beforeAttach, Context expectedCurrent) {
        Context current = current();
        if (current == expectedCurrent) {
            local.set(beforeAttach);
        }
    }

    private static final class ScopeImpl implements Scope {
        private final ThreadLocalContextStorage storage;
        private final Context beforeAttach;
        private final Context expectedCurrent;
        private boolean closed;

        private ScopeImpl(ThreadLocalContextStorage storage, Context beforeAttach, Context expectedCurrent) {
            this.storage = storage;
            this.beforeAttach = beforeAttach;
            this.expectedCurrent = expectedCurrent;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            storage.restore(beforeAttach, expectedCurrent);
        }
    }
}
