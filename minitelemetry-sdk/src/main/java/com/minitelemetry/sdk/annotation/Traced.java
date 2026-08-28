package com.minitelemetry.sdk.annotation;

import com.minitelemetry.sdk.trace.SpanKind;

import java.lang.annotation.*;

/**
 * 标记方法自动创建 Trace Span。
 *
 * <p>value() 即 spanName;
 * 留空时由 TracedSpanNameResolver 回退为 声明类全名.方法名。
 *
 * <p>localRoot() 显式声明本 span 是“服务在这条 trace 里的局部根”。
 * 默认 false，由 SDK 自动判定（root / remote parent -> true, local child -> false）。
 * 业务方在以下场景，应主动置 true，保证 L1 服务级入口可准确识别到并进入口：
 *
 * <ul>
 *   <li>MQ Consumer 手写的消息处理方法（未走标准 Consumer 拦截器）</li>
 *   <li>@Scheduled 定时任务入口</li>
 *   <li>通过 CLI / 启动脚本主动触发的入口</li>
 *   <li>任何由外部机制触发但不属于标准 Servlet/Consumer 埋点的入口</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Traced {
    /**
     * spanName; 空则回退声明类全名.方法名。
     */
    String value() default "";

    /**
     * span 类型; 默认 INTERNAL。
     */
    SpanKind kind() default SpanKind.INTERNAL;

    /**
     * 是否显式标记为本服务的 local root（服务级入口）。
     * 默认 false 表示 SpanBuilder 自动判定；
     * 显式置 true 会强制覆盖自动判定，
     * 无论 parent 是否为本进程 span，都视作服务入口。
     */
    boolean localRoot() default false;
}
