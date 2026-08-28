package com.minitelemetry.sdk.trace.export;

import com.minitelemetry.sdk.trace.ReadableSpan;

/**
 * Span 生命周期处理器,通过 {@code TracerRuntime.install(...)} 注册到全局。
 * <p>多 processor 同时注册时彼此独立。生产默认实现 {@link BatchSpanProcessor}。
 */
public interface SpanProcessor {

    /** Span start 后立即调用,默认 no-op。 */
    default void onStart(ReadableSpan span) {
    }

    /** Span end 时调用。是否真的 export 由实现自行判定(一般跳过 sampled=false)。 */
    void onEnd(ReadableSpan span);

    /** 强制 flush 内部 buffer。 */
    default ResultCode flush() {
        return ResultCode.SUCCESS;
    }

    /** 释放资源:停 worker、关闭 exporter 等。 */
    default ResultCode shutdown() {
        return ResultCode.SUCCESS;
    }
}
