package com.minitelemetry.sdk.context;

/**
 * 当前线程活动 {@link Context} 变更的监听器,用于把 trace 上下文同步到线程局部设施(如日志 MDC)。
 *
 * <p><b>所有实现必须遵守</b>:
 * <ul>
 *   <li>永不抛异常、不阻塞——回调位于 attach/restore 热路径,任何耗时或异常直接影响业务线程。</li>
 *   <li>不得修改 Context——仅可读取入参并同步到外部设施。</li>
 *   <li>回调语义为<b>幂等全量同步</b>:收到通知即把目标设施重写为 {@code context} 对应状态,
 *       不区分进入或退出作用域,故实现无需维护任何状态。</li>
 * </ul>
 */
public interface ContextListener {
    /**
     * 当前线程活动 Context 已变更。
     *
     * @param context 变更后的 Context;{@code null} 表示当前线程已无活动 Context
     */
    void onContextChanged(Context context);
}
