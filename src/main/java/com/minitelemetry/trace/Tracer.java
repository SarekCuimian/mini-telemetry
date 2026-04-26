package com.minitelemetry.trace;

import com.minitelemetry.exporter.SpanExporter;

/**
 * Span 的创建入口。
 *
 * <p>{@link Tracer} 由 {@link TracerProvider} 管理并复用；业务代码通常通过
 * {@code MiniTelemetry.getTracer(...)} 获取它，而不是直接创建。</p>
 */
public final class Tracer {
    private final String name;
    private final TracerProvider provider;

    /**
     * 创建一个新的 Tracer。
     *
     * @param name Tracer 名称
     * @param provider Tracer 所属 Provider
     */
    Tracer(String name, TracerProvider provider) {
        this.name = java.util.Objects.requireNonNull(name, "name");
        this.provider = java.util.Objects.requireNonNull(provider, "provider");
    }

    /**
     * 返回 Tracer 名称。
     *
     * @return Tracer 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 返回当前 Tracer 绑定的导出器。
     *
     * <p>该方法仅供 trace 包内部使用。</p>
     */
    SpanExporter getExporter() {
        return provider.getExporter();
    }

    /**
     * 创建一个新的 Span 构建器。
     *
     * @param spanName Span 名称
     * @return Span 构建器
     */
    public SpanBuilder spanBuilder(String spanName) {
        return new SpanBuilder(this, spanName);
    }
}
