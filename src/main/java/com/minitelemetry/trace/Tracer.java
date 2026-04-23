package com.minitelemetry.trace;

import java.util.Objects;
import com.minitelemetry.exporter.SpanExporter;

/**
 * Span 的创建入口。
 *
 * <p>当前实现中，{@link Tracer} 负责持有导出器并创建 {@link SpanBuilder}。</p>
 */
public final class Tracer {
    private final String name;
    private final SpanExporter exporter;

    /**
     * 创建一个新的 Tracer。
     *
     * @param name Tracer 名称
     * @param exporter Span 导出器
     */
    public Tracer(String name, SpanExporter exporter) {
        this.name = Objects.requireNonNull(name, "name");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
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
        return exporter;
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
