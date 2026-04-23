package com.minitelemetry.trace;

import java.util.UUID;

/**
 * 生成 traceId 和 spanId 的简单工具类。
 */
public final class IdGenerator {
    private IdGenerator() {
    }

    /**
     * 生成 32 位十六进制 traceId。
     *
     * @return 新的 traceId
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 16 位十六进制 spanId。
     *
     * @return 新的 spanId
     */
    public static String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
