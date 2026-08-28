package com.minitelemetry.sdk.trace.sampling.strategy;

import com.minitelemetry.sdk.trace.sampling.Sampler;
import com.minitelemetry.sdk.trace.sampling.SamplingResult;

import java.util.Objects;

/**
 * 按 root span name 从 {@link SamplingStrategyStorage} 选择策略的采样器。
 * <p>线程安全。子 span 复用父决策,不再匹配。
 *
 * <p>匹配三级兜底:精确 spanName -> wildcard {@code "*"} -> 硬编码 drop(策略表为空时的保守兜底)。
 * <p>错误翻牌每日上限由 {@link ErrorLimiter} 在 export 阶段独立实现,二者共享同一 storage。
 */
public final class StrategyBasedSampler implements Sampler {

    public static final String WILDCARD = "*";

    private final SamplingStrategyStorage storage;

    public StrategyBasedSampler(SamplingStrategyStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public SamplingResult shouldSample(String traceId, String spanName, boolean hasParent, boolean parentSampled) {
        if (hasParent) {
            if (parentSampled) {
                return SamplingResult.recordAndSample();
            }
            SamplingStrategy strategy = resolveStrategy(spanName);
            return strategy != null && strategy.isKeepErrors()
                    ? SamplingResult.recordOnly()
                    : SamplingResult.drop();
        }
        SamplingStrategy strategy = resolveStrategy(spanName);
        if (strategy == null) {
            return SamplingResult.drop();
        }
        return applyStrategy(strategy, traceId, spanName);
    }

    private SamplingStrategy resolveStrategy(String spanName) {
        SamplingStrategy strategy = storage.get(spanName);
        if (strategy == null) {
            strategy = storage.get(WILDCARD);
        }
        return strategy;
    }

    private static SamplingResult applyStrategy(SamplingStrategy strategy, String traceId, String spanName) {
        SamplingResult ratioResult = strategy.getRatioBasedSampler()
                .shouldSample(traceId, spanName, false, false);
        if (ratioResult.isSampled()) {
            return ratioResult;
        }
        return strategy.isKeepErrors() ? SamplingResult.recordOnly() : SamplingResult.drop();
    }

    @Override
    public String getDescription() { return "StrategyBased{strategies=" + storage.size() + "}"; }
}
