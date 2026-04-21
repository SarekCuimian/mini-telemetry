package com.minitelemetry.trace;

import java.util.UUID;

public final class IdGenerator {
    private IdGenerator() {
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
