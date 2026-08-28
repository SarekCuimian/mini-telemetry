package com.minitelemetry.sdk.trace.sampling.strategy;

import java.util.Map;
import java.util.Objects;

/**
 * 远端采样策略的本地权威副本,只做“原子全量替换 + 按 key 查询”。
 * <p>线程安全:写端 volatile 引用替换 + {@link Map#copyOf} 不可变快照;读端无锁。
 * 由 {@link StrategyBasedSampler}(采样阶段)与 {@link ErrorLimiter}(导出阶段)共享。
 */
public final class SamplingStrategyStorage {

    private volatile Map<String, SamplingStrategy> strategies = Map.of();

    /** 全量替换。Poller 每次拉取成功调用一次。 */
    public void update(Map<String, SamplingStrategy> next) {
        Objects.requireNonNull(next, "next");
        this.strategies = Map.copyOf(next);
    }

    /** 单 key 查询;wildcard fallback 由调用方按需自行组合。 */
    public SamplingStrategy get(String key) { return strategies.get(key); }

    public int size() { return strategies.size(); }
}
