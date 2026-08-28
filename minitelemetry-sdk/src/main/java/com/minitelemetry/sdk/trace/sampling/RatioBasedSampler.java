package com.minitelemetry.sdk.trace.sampling;

import java.util.Objects;

/**
 * 按 traceId 末 64 bit 哈希的概率采样器,同 traceId 在所有进程结果一致,保证分布式链路完整。
 *
 * <p>不可变,线程安全。通过 {@link Sampler#ratioBased(double)} 构造。
 */
final class RatioBasedSampler implements Sampler {

    private static final SamplingResult POSITIVE = SamplingResult.recordAndSample();
    private static final SamplingResult NEGATIVE = SamplingResult.drop();

    private final long idUpperBound;
    private final String description;

    RatioBasedSampler(double ratio) {
        if (ratio < 0.0d || ratio > 1.0d) {
            throw new IllegalArgumentException("ratio must be in [0,1], got " + ratio);
        }
        if (ratio == 0.0d) {
            this.idUpperBound = Long.MIN_VALUE;
        } else if (ratio == 1.0d) {
            this.idUpperBound = Long.MAX_VALUE;
        } else {
            this.idUpperBound = (long) (Long.MAX_VALUE * ratio);
        }
        this.description = "TraceIdRatio{" + ratio + "}";
    }

    @Override
    public SamplingResult shouldSample(String traceId, String spanName, boolean hasParent, boolean parentSampled) {
        return Math.abs(traceIdLowerHalfToLong(Objects.requireNonNull(traceId, "traceId"))) < idUpperBound
                ? POSITIVE
                : NEGATIVE;
    }

    @Override
    public String getDescription() { return description; }

    /** 取 traceId 末 16 hex 解析为 long。 */
    private static long traceIdLowerHalfToLong(String traceId) {
        String tail = traceId.length() >= 16 ? traceId.substring(traceId.length() - 16) : traceId;
        return Long.parseUnsignedLong(tail, 16);
    }
}
