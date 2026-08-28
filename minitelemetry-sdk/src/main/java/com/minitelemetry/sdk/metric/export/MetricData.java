package com.minitelemetry.sdk.metric.export;

/** 批量 {@code metrics[]} 单条元素;{@code serviceName} 由上报壳顶层携带。 */
public final class MetricData {

    private final String spanName;

    /** SpanKind 名,如 {@code SERVER}/{@code CLIENT};老客户端未上报时为 {@code null}。 */
    private final String kind;

    private final int successCount;
    private final int failureCount;
    private final long totalElapsedTime;
    private final long reportTime;
    private long maxElapsedTime;

    public MetricData(String spanName,
                      String kind,
                      int successCount,
                      int failureCount,
                      long totalElapsedTime,
                      long reportTime,
                      long maxElapsedTime) {
        this.spanName = spanName;
        this.kind = (kind == null || kind.isBlank()) ? null : kind;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalElapsedTime = totalElapsedTime;
        this.reportTime = reportTime;
        this.maxElapsedTime = maxElapsedTime;
    }

    public long getMaxElapsedTime() {
        return maxElapsedTime;
    }

    public void setMaxElapsedTime(long maxElapsedTime) {
        this.maxElapsedTime = maxElapsedTime;
    }

    public String getSpanName() {
        return spanName;
    }

    public String getKind() {
        return kind;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public long getTotalElapsedTime() {
        return totalElapsedTime;
    }

    public long getReportTime() {
        return reportTime;
    }
}
