package com.minitelemetry.sdk.trace.export;

import com.minitelemetry.sdk.trace.ReadableSpan;

import java.util.Collection;

/**
 * 把一批已经准备好的 span 发到目的地。无状态,调度策略由 {@code SpanProcessor} 决定。
 * <p>实现要求:线程安全、不抛异常(转 {@link ResultCode})、不修改入参 collection。
 */
public interface SpanExporter {

    ResultCode export(Collection<ReadableSpan> spans);

    default ResultCode flush() {
        return ResultCode.SUCCESS;
    }

    default ResultCode shutdown() {
        return ResultCode.SUCCESS;
    }
}
