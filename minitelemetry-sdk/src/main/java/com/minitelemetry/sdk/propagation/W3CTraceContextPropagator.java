package com.minitelemetry.sdk.propagation;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * W3C Trace Context 传播器(https://www.w3.org/TR/trace-context/)。
 *
 * <pre>traceparent: 00-{32位小写hex traceId}-{16位小写hex spanId}-{2位hex flags}</pre>
 *
 * <p>与 {@code IdGenerator} 的 128bit/64bit 小写 hex 完全对齐,编解码零转换。
 * flags 仅使用最低位 sampled;其余位忽略。
 *
 * <p>无状态单例,线程安全。
 */
public final class W3CTraceContextPropagator implements TextMapPropagator {

    public static final String TRACE_PARENT = "traceparent";

    private static final Logger log = LoggerFactory.getLogger(W3CTraceContextPropagator.class);
    private static final W3CTraceContextPropagator INSTANCE = new W3CTraceContextPropagator();
    private static final Collection<String> FIELDS = List.of(TRACE_PARENT);

    /** 00-traceId(32)-spanId(16)-flags(2) 的固定长度与分隔符位置。 */
    private static final int HEADER_LENGTH = 55;
    private static final int TRACE_ID_START = 3;
    private static final int TRACE_ID_END = 35;
    private static final int SPAN_ID_START = 36;
    private static final int SPAN_ID_END = 52;
    private static final int FLAGS_START = 53;
    private static final String VERSION_00 = "00";
    private static final String VERSION_INVALID = "ff";

    private W3CTraceContextPropagator() {
    }

    public static W3CTraceContextPropagator getInstance() {
        return INSTANCE;
    }

    @Override
    public Collection<String> fields() {
        return FIELDS;
    }

    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
        if (context == null || setter == null) {
            return;
        }
        SpanContext spanContext = spanContextOf(context);
        if (spanContext == null || !isValidIds(spanContext)) {
            return;
        }
        String header = VERSION_00 + '-' + spanContext.getTraceId() + '-' + spanContext.getSpanId()
                + (spanContext.isSampled() ? "-01" : "-00");
        try {
            setter.set(carrier, TRACE_PARENT, header);
        } catch (Throwable t) {
            log.debug("traceparent inject failed, ignored", t);
        }
    }

    @Override
    public <C> Context extract(Context parent, C carrier, TextMapGetter<C> getter) {
        Context base = parent == null ? Context.root() : parent;
        if (carrier == null || getter == null) {
            return base;
        }
        String header;
        try {
            header = getter.get(carrier, TRACE_PARENT);
        } catch (Throwable t) {
            log.debug("traceparent read failed, ignored", t);
            return base;
        }
        SpanContext remote = parseTraceParent(header);
        // remote 以占位 Span 形式挂进与本地 Span 同一个 slot;
        // Span.current()/currentTraceId() 立即可见上游,SpanBuilder 自动完成 remote 父子关系
        return remote == null ? base : Span.wrap(remote).storeInContext(base);
    }

    /** 取上下文里的 span 上下文;占位 Span(纯转发场景)与本地 Span 同 slot,统一路径。 */
    private static SpanContext spanContextOf(Context context) {
        Span span = Span.fromContext(context);
        return span == null ? null : span.getSpanContext();
    }

    /** 防御脏数据(如老版 dto 链路的非标 ID):ID 长度不符时拒绝写出 header。 */
    private static boolean isValidIds(SpanContext spanContext) {
        return spanContext.getTraceId().length() == TRACE_ID_END - TRACE_ID_START
                && spanContext.getSpanId().length() == SPAN_ID_END - SPAN_ID_START;
    }

    /** 解析 traceparent;任何不合法返回 {@code null},永不抛异常。 */
    static SpanContext parseTraceParent(String header) {
        if (header == null || header.length() < HEADER_LENGTH) {
            return null;
        }
        if (header.charAt(2) != '-' || header.charAt(TRACE_ID_END) != '-'
                || header.charAt(SPAN_ID_END) != '-') {
            return null;
        }
        if (!isLowercaseHex(header, 0, 2)
                || !isLowercaseHex(header, TRACE_ID_START, TRACE_ID_END)
                || !isLowercaseHex(header, SPAN_ID_START, SPAN_ID_END)
                || !isLowercaseHex(header, FLAGS_START, HEADER_LENGTH)) {
            return null;
        }
        String version = header.substring(0, 2);
        if (VERSION_INVALID.equals(version)) {
            return null;
        }
        // version 00 必须精确 55 位;未来版本允许更长,但第 55 位必须是段分隔符(W3C 宽容解析规则)
        if (VERSION_00.equals(version) && header.length() != HEADER_LENGTH) {
            return null;
        }
        if (!VERSION_00.equals(version) && header.length() > HEADER_LENGTH
                && header.charAt(HEADER_LENGTH) != '-') {
            return null;
        }
        String traceId = header.substring(TRACE_ID_START, TRACE_ID_END);
        String spanId = header.substring(SPAN_ID_START, SPAN_ID_END);
        if (isAllZero(traceId) || isAllZero(spanId)) {
            return null;
        }
        boolean sampled = (hexValue(header.charAt(HEADER_LENGTH - 1)) & 1) == 1;
        return SpanContext.createRemote(traceId, spanId, sampled);
    }

    private static boolean isLowercaseHex(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private static int hexValue(char c) {
        return c <= '9' ? c - '0' : c - 'a' + 10;
    }
}
