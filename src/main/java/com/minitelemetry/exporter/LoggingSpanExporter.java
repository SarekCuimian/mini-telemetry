package com.minitelemetry.exporter;

import java.time.Instant;
import com.minitelemetry.trace.ReadableSpan;

public final class LoggingSpanExporter implements SpanExporter {
    @Override
    public void export(ReadableSpan span) {
        long durationMs = span.getEndEpochMillis() - span.getStartEpochMillis();
        System.out.println("--------------------------------------------------");
        System.out.println("tracer      : " + span.getTracerName());
        System.out.println("span        : " + span.getName());
        System.out.println("kind        : " + span.getKind());
        System.out.println("traceId     : " + span.getSpanContext().getTraceId());
        System.out.println("spanId      : " + span.getSpanContext().getSpanId());
        System.out.println("parentSpanId: " + span.getParentSpanId());
        System.out.println("status      : " + span.getStatusCode() + " " + span.getStatusMessage());
        System.out.println("thread      : " + span.getThreadName());
        System.out.println("start       : " + Instant.ofEpochMilli(span.getStartEpochMillis()));
        System.out.println("end         : " + Instant.ofEpochMilli(span.getEndEpochMillis()));
        System.out.println("durationMs  : " + durationMs);
        System.out.println("attributes  : " + span.getAttributes());
    }
}
