package com.minitelemetry.sdk.metric;

/** {@link Metric} 单次拉取快照,不可变。 */
public record MetricSnapshot(
        int successCount,
        int failureCount,
        long totalElapsedTime,
        long maxElapsedTime
) {
}
