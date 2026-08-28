package com.minitelemetry.sdk.trace;

/** Span 状态码。ERROR 触发 trace 级错误翻牌,详见 Span.setStatus。 */
public enum StatusCode {
    UNSET,
    OK,
    ERROR
}
