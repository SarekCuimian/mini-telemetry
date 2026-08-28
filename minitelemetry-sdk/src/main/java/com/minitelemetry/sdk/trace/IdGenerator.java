package com.minitelemetry.sdk.trace;

import java.util.concurrent.ThreadLocalRandom;

/**
 * W3C TraceContext 标准 ID 生成器:128 bit traceId / 64 bit spanId,小写 hex。
 * <p>{@link ThreadLocalRandom} 提供 hot-path 友好的均匀分布(避免 UUID v4 variant 引入的偏置),
 * 复用 {@link ThreadLocal} char 缓冲区 + 手写 hex 编码,绕开 {@code String.format} Formatter 状态机。
 */
final class IdGenerator {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /** traceId 占满 32 位,spanId 复用前 16 位。 */
    private static final ThreadLocal<char[]> HEX_BUF = ThreadLocal.withInitial(() -> new char[32]);

    private IdGenerator() {
    }

    /** 生成非 0 的 128 bit traceId(32 位 hex)。 */
    static String generateTraceId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long high;
        long low;
        do {
            high = random.nextLong();
        } while (high == 0L);
        do {
            low = random.nextLong();
        } while (low == 0L);

        char[] buf = HEX_BUF.get();
        longToHex(high, buf, 0);
        longToHex(low, buf, 16);
        return new String(buf, 0, 32);
    }

    /** 生成非 0 的 64 bit spanId(16 位 hex)。 */
    static String generateSpanId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long id;
        do {
            id = random.nextLong();
        } while (id == 0L);

        char[] buf = HEX_BUF.get();
        longToHex(id, buf, 0);
        return new String(buf, 0, 16);
    }

    private static void longToHex(long value, char[] dest, int off) {
        for (int i = 0; i < 8; i++) {
            int b = (int) ((value >>> ((7 - i) << 3)) & 0xFF);
            dest[off + (i << 1)] = HEX_CHARS[b >>> 4];
            dest[off + (i << 1) + 1] = HEX_CHARS[b & 0x0F];
        }
    }
}
