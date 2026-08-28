package com.minitelemetry.sdk.trace.export;

import com.minitelemetry.sdk.trace.ReadableSpan;
import com.minitelemetry.sdk.trace.SpanContext;

import java.util.Map;

/**
 * Span 上报 DTO,{@link HttpSpanExporter} 序列化用。
 * <p>不可变。{@link #from(ReadableSpan)} 一次性快照后续不变。
 *
 * <p>独立于 {@link ReadableSpan} 的目的:把平 {@link SpanContext} 嵌套字段、
 * 把内部枚举显式 {@code .name()} 锁定字符串契约,解耦内部数据结构与对外上报格式。
 */
public final class SpanData {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final String kind;
    private final long startEpochMillis;
    private final long endEpochMillis;
    private final long durationMillis;
    private final String statusCode;
    private final String statusMessage;
    private final String threadName;
    private final Map<String, Object> attributes;
    /** 本服务在此 trace 中的局部根(kind=SERVER 入口 / Consumer 入口 / @Traced(localRoot=true) 声明的入口)。 */
    private final boolean localRoot;

    public SpanData(String traceId,
                    String spanId,
                    String parentSpanId,
                    String name,
                    String kind,
                    long startEpochMillis,
                    long endEpochMillis,
                    long durationMillis,
                    String statusCode,
                    String statusMessage,
                    String threadName,
                    Map<String, Object> attributes,
                    boolean localRoot) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.kind = kind;
        this.startEpochMillis = startEpochMillis;
        this.endEpochMillis = endEpochMillis;
        this.durationMillis = durationMillis;
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.threadName = threadName;
        this.attributes = attributes;
        this.localRoot = localRoot;
    }

    public static SpanData from(ReadableSpan s) {
        SpanContext ctx = s.getSpanContext();
        long start = s.getStartEpochMillis();
        long end = s.getEndEpochMillis();
        return new SpanData(
                ctx.getTraceId(),
                ctx.getSpanId(),
                s.getParentSpanId(),
                s.getName(),
                s.getKind() == null ? null : s.getKind().name(),
                start,
                end,
                // 时钟回拨防御
                Math.max(0L, end - start),
                s.getStatusCode() == null ? null : s.getStatusCode().name(),
                s.getStatusMessage(),
                s.getThreadName(),
                s.getAttributes(),
                s.isLocalRoot()
        );
    }

    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public long getStartEpochMillis() { return startEpochMillis; }
    public long getEndEpochMillis() { return endEpochMillis; }
    public long getDurationMillis() { return durationMillis; }
    public String getStatusCode() { return statusCode; }
    public String getStatusMessage() { return statusMessage; }
    public String getThreadName() { return threadName; }
    public Map<String, Object> getAttributes() { return attributes; }
    public boolean isLocalRoot() { return localRoot; }
}
