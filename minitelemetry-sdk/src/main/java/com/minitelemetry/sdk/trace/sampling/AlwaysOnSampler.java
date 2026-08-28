package com.minitelemetry.sdk.trace.sampling;

/** 全采样。无状态单例,通过 {@link Sampler#alwaysOn()} 获取。 */
final class AlwaysOnSampler implements Sampler {

    static final AlwaysOnSampler INSTANCE = new AlwaysOnSampler();

    private AlwaysOnSampler() {
    }

    @Override
    public SamplingResult shouldSample(String traceId, String spanName, boolean hasParent, boolean parentSampled) {
        return SamplingResult.recordAndSample();
    }

    @Override
    public String getDescription() { return "AlwaysOn"; }
}
