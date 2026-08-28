package com.minitelemetry.sdk.metric.export;

import com.minitelemetry.sdk.metric.Metric;
import com.minitelemetry.sdk.metric.MetricSnapshot;
import com.minitelemetry.sdk.trace.ReadableSpan;
import com.minitelemetry.sdk.trace.StatusCode;
import com.minitelemetry.sdk.trace.export.SpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 把 end 的 span 聚合成 {@code (spanName, kind)} 维度的成功/失败计数与耗时,输出由 {@link MetricExporter} 周期上报。
 * <p>不受采样影响 —— 所有结束的 span 都被统计,与 "sampled=false 直接丢弃" 的导出类 processor 行为不同。
 *
 * <p>构造时把自身写入 {@link #INSTANCE} 静态字段,供非 Spring 管理的老 {@code dto.Tracer} 经
 * {@link #getInstance()} 零依赖访问。volatile 保证装配线程的写对业务线程可见,
 * 同时禁止“对象引用先逃逸、字段未初始化”的指令重排。
 */
public class SpanMetricProcessor implements SpanProcessor {

    private static final Logger log = LoggerFactory.getLogger(SpanMetricProcessor.class);

    private static volatile SpanMetricProcessor INSTANCE;

    private final ConcurrentMap<MetricKey, Metric> spanMetricsMap = new ConcurrentHashMap<>();

    public SpanMetricProcessor() {
        INSTANCE = this;
    }

    /** Spring 装配完成前返回 {@code null},调用方需自行判空。 */
    public static SpanMetricProcessor getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        long elapsedTime = Math.max(0L, span.getEndEpochMillis() - span.getStartEpochMillis());
        boolean success = span.getStatusCode() != StatusCode.ERROR;
        String kind = span.getKind() == null ? "" : span.getKind().name();
        recordMetrics(span.getName(), kind, success, elapsedTime);
    }

    /** 老 {@code dto.Tracer} 无 kind,落入空 kind 桶。 */
    public void recordMetrics(String spanName, boolean success, long elapsedTime) {
        recordMetrics(spanName, "", success, elapsedTime);
    }

    public void recordMetrics(String spanName, String kind, boolean success, long elapsedTime) {
        try {
            if (spanName == null || spanName.isBlank()) {
                return;
            }
            MetricKey key = new MetricKey(spanName, kind);
            Metric metric = spanMetricsMap.computeIfAbsent(key, k -> new Metric());
            if (success) {
                metric.incrementSuccess();
            } else {
                metric.incrementFailure();
            }
            metric.addElapsedTime(elapsedTime);
            metric.updateMaxElapsedTime(elapsedTime);
        } catch (Exception e) {
            log.error("aggregation failed", e);
        }
    }

    /** 当前已出现过的聚合键只读快照,迭代顺序不保证。 */
    public Set<MetricKey> getMetricKeys() {
        return Set.copyOf(spanMetricsMap.keySet());
    }

    public MetricSnapshot getMetricSnapshot(MetricKey key) {
        if (key == null || key.spanName().isBlank()) {
            return null;
        }
        Metric metric = spanMetricsMap.get(key);
        return metric == null ? null : metric.getSnapshotAndResetMax();
    }

    public Metric getMetric(MetricKey key) {
        return key == null ? null : spanMetricsMap.get(key);
    }
}
