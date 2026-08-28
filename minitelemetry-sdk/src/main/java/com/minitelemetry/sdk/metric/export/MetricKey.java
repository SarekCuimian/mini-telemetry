package com.minitelemetry.sdk.metric.export;

/**
 * 进程内 metric 聚合键:同名 span 按 {@code kind} 分桶,避免 CLIENT/SERVER 混在同一计数器。
 * 缺省 kind 记为空串,与服务端“未上报 kind -> 空桶”对齐。
 */
public record MetricKey(String spanName, String kind) {

    public MetricKey {
        spanName = spanName == null ? "" : spanName;
        kind = kind == null || kind.isBlank() ? "" : kind;
    }
}
