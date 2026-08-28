package com.minitelemetry.sdk.trace;

/** Span 类型,语义对齐 OTel SpanKind。 */
public enum SpanKind {
    INTERNAL,
    SERVER,
    CLIENT,
    PRODUCER,
    CONSUMER
}
