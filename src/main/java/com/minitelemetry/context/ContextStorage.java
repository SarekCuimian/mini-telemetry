package com.minitelemetry.context;

/**
 * 抽象当前线程上下文的存取与激活行为。
 */
public interface ContextStorage {
    /**
     * 返回当前线程已绑定的上下文；如果当前线程尚未绑定，则返回根上下文。
     *
     * @return 当前线程的活动上下文
     */
    Context current();

    /**
     * 将给定上下文激活到当前线程，并返回对应的恢复句柄。
     *
     * @param context 要绑定到当前线程的上下文
     * @return 用于恢复旧上下文的作用域句柄
     */
    Scope attach(Context context);

    /**
     * 清除当前线程已绑定的上下文。
     */
    void clear();
}
