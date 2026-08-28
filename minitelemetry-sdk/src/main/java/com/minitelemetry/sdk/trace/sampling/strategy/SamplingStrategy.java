package com.minitelemetry.sdk.trace.sampling.strategy;

import com.minitelemetry.sdk.trace.sampling.Sampler;

import java.util.Objects;

/**
 * 远端下发的单条采样策略,绑定一个 root span name 的采样率与错误保留规则。
 * <p>不可变。{@link StrategyBasedSampler} 构造时一次性创建,运行时无对象分配。
 */
public final class SamplingStrategy {

    private final String rootSpanName;
    private final double ratio;
    private final boolean keepErrors;
    private final int dailyErrorLimit;
    private final Sampler ratioBasedSampler;

    public SamplingStrategy(String rootSpanName, double ratio, boolean keepErrors, int dailyErrorLimit) {
        this.rootSpanName = Objects.requireNonNull(rootSpanName, "rootSpanName");
        this.ratio = ratio;
        this.keepErrors = keepErrors;
        this.dailyErrorLimit = dailyErrorLimit;
        this.ratioBasedSampler = Sampler.ratioBased(ratio);
    }

    public String getRootSpanName() { return rootSpanName; }
    public double getRatio() { return ratio; }
    public boolean isKeepErrors() { return keepErrors; }
    public int getDailyErrorLimit() { return dailyErrorLimit; }
    Sampler getRatioBasedSampler() { return ratioBasedSampler; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SamplingStrategy that)) return false;
        return Double.compare(that.ratio, ratio) == 0
                && keepErrors == that.keepErrors
                && dailyErrorLimit == that.dailyErrorLimit
                && Objects.equals(rootSpanName, that.rootSpanName);
    }

    @Override
    public int hashCode() { return Objects.hash(rootSpanName, ratio, keepErrors, dailyErrorLimit); }

    @Override
    public String toString() {
        return "SamplingStrategy{rootSpanName=" + rootSpanName
                + ", ratio=" + ratio
                + ", keepErrors=" + keepErrors
                + ", dailyErrorLimit=" + dailyErrorLimit + "}";
    }
}
