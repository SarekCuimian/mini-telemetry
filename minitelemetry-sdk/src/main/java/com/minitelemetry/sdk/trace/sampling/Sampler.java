package com.minitelemetry.sdk.trace.sampling;

/**
 * 决定一条 span 是否记录 / 导出。实现必须线程安全(通常无状态)。
 * <p>构造通过本接口静态工厂方法。
 */
public interface Sampler {

    SamplingResult shouldSample(String traceId, String spanName, boolean hasParent, boolean parentSampled);

    /** 用于日志与诊断的简短描述。 */
    String getDescription();

    static Sampler alwaysOn() { return AlwaysOnSampler.INSTANCE; }

    static Sampler alwaysOff() { return AlwaysOffSampler.INSTANCE; }

    /** 按 traceId 末 64 bit 哈希命中概率 {@code ratio} 采样,同 trace 在所有进程结果一致。 */
    static Sampler ratioBased(double ratio) { return new RatioBasedSampler(ratio); }

    /** 默认 alwaysOff,避免无策略时数据爆炸。 */
    static Sampler defaultSampler() { return alwaysOff(); }
}
