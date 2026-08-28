package com.minitelemetry.sdk.propagation;

import com.minitelemetry.sdk.context.Context;

import java.util.Collection;

/**
 * 跨进程上下文传播器:把 trace 上下文编码进出站载体(inject),或从入站载体还原(extract)。
 *
 * <p><b>硬性契约(所有实现必须遵守)</b>:
 * <ul>
 *   <li>{@link #extract} 永不抛异常 —— 任何畸形/缺失 header 一律返回入参 {@code parent} 原样,
 *       不能自动降级为新 trace root,传播故障绝不允许影响业务请求</li>
 *   <li>{@link #inject} 永不抛异常 —— 当前无有效上下文时静默不写;setter 内部异常须吞掉</li>
 *   <li>二者在 {@code TracerRuntime.install()} 之前调用必须安全(此时 span 本就会被静默 drop)</li>
 * </ul>
 */
public interface TextMapPropagator {

    /**
     * 本协议会写入/读取的全部 key(如 {@code ["traceparent"]})。
     * <p>供载体做预清理(MQ 转发防止 key 污染)或 header 白名单配置。返回不可变集合。
     */
    Collection<String> fields();

    /**
     * 把 {@code context} 中的 span 上下文编码写入 carrier。
     *
     * @param context 取上下文的来源,常为 {@code Context.current()};null 安全
     * @param carrier 出站载体;null 安全(no-op)
     * @param setter 载体写适配器
     */
    <C> void inject(Context context, C carrier, TextMapSetter<C> setter);

    /**
     * 从 carrier 还原 span 上下文,挂到 {@code parent} 的副本上返回。
     *
     * @param parent 还原失败时原样返回的基底,常为 {@code Context.current()};null 视为 {@code Context.root()}
     * @param carrier 入站载体;null 安全(返回 parent)
     * @param getter 载体读适配器
     * @return 带 remote 上下文的新 Context;还原失败时为 {@code parent} 本身
     */
    <C> Context extract(Context parent, C carrier, TextMapGetter<C> getter);
}
