package com.minitelemetry.exporter;

import com.minitelemetry.trace.ReadableSpan;

/**
 * Span 导出器抽象。
 */
public interface SpanExporter {
    /**
     * 导出一个已结束的 Span 快照。
     *
     * @param span 要导出的 Span 只读视图
     */
    void export(ReadableSpan span);
}
