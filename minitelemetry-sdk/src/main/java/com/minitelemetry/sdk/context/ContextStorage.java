package com.minitelemetry.sdk.context;

import java.util.Objects;

/**
 * Context 的进程内存储,基于 {@link ThreadLocal}。
 * <p>包私有 final,由 {@code Context.STORAGE} 持有的唯一实例对外提供能力。
 */
final class ContextStorage {
    /** 上下文变更监听器,进程级单个。未设置时钩子退化为一次 volatile 读。 */
    private static volatile ContextListener listener;

    private final ThreadLocal<Context> local = new ThreadLocal<>();

    static void setListener(ContextListener contextListener) {
        listener = contextListener;
    }

    Context current() {
        return local.get();
    }

    Scope attach(Context toAttach) {
        Objects.requireNonNull(toAttach, "toAttach");

        Context beforeAttach = current();
        if (beforeAttach == toAttach) {
            return Scope.noop();
        }

        local.set(toAttach);
        notifyListener(toAttach);
        return new Scope(this, beforeAttach, toAttach);
    }

    void clear() {
        local.remove();
        notifyListener(null);
    }

    void restore(Context beforeAttach, Context toAttach) {
        Context current = current();
        if (current == toAttach) {
            if (beforeAttach == null) {
                local.remove();
            } else {
                local.set(beforeAttach);
            }
            notifyListener(beforeAttach);
        }
    }

    /** 通知监听器。监听器故障绝不能影响业务,异常一律吞掉。 */
    private static void notifyListener(Context context) {
        ContextListener current = listener;
        if (current == null) {
            return;
        }
        try {
            current.onContextChanged(context);
        } catch (Throwable ignored) {
            // ContextListener 契约要求永不抛异常,此处兜底
        }
    }
}
