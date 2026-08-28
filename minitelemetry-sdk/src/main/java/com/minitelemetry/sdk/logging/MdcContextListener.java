package com.minitelemetry.sdk.logging;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.context.ContextListener;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.SpanContext;
import org.slf4j.MDC;

/**
 * 把当前 span 的 traceId/spanId 同步进 SLF4J {@link MDC},使业务日志自动携带链路标识。
 *
 * <p>业务侧在日志模板引用对应 key 才会输出,如 Logback 的 {@code %X{traceId}}。
 *
 * <p>每次回调按目标 Context 全量重写:有 span 则覆盖两个 key,无 span 则移除。
 * 因此嵌套 span 退出时 MDC 自动还原为外层 span 的值,而非被清空。
 */
public final class MdcContextListener implements ContextListener {

    /** traceId 写入的 MDC key,与日志模板 {@code %X{traceId}} 对应。 */
    public static final String TRACE_ID_KEY = "traceId";

    /** spanId 写入的 MDC key,与日志模板 {@code %X{spanId}} 对应。 */
    public static final String SPAN_ID_KEY = "spanId";

    @Override
    public void onContextChanged(Context context) {
        Span span = context == null ? null : Span.fromContext(context);
        if (span == null) {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(SPAN_ID_KEY);
            return;
        }
        SpanContext spanContext = span.getSpanContext();
        MDC.put(TRACE_ID_KEY, spanContext.getTraceId());
        MDC.put(SPAN_ID_KEY, spanContext.getSpanId());
    }
}
