package com.minitelemetry.sdk.trace.sampling;

/**
 * 全丢弃。无状态单例,通过 {@link Sampler#alwaysOff()} 获取。
 * <p>仅影响 trace 导出;metric 在 {@code Span.end()} 中无条件聚合,不受影响。
 */
final class AlwaysOffSampler implements Sampler {

    static final AlwaysOffSampler INSTANCE = new AlwaysOffSampler();

    private AlwaysOffSampler() {
    }

    @Override
    public SamplingResult shouldSample(String traceId, String spanName, boolean hasParent, boolean parentSampled) {
        return SamplingResult.drop();
    }

    @Override
    public String getDescription() { return "AlwaysOff"; }
}
