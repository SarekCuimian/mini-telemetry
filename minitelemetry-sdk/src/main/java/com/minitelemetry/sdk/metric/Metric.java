package com.minitelemetry.sdk.metric;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单个 {@code (spanName, kind)} 桶的成功/失败计数与耗时聚合,线程安全(全部基于原子类)。
 * <p>计数采用“扣减式”语义:业务侧累加,上报成功后 {@link #deductReported} 抵扣;
 * 失败时通过 {@link #mergeMaxElapsed} 把已快照的 max 合并回去,避免上报失败丢失高位水位。
 */
public class Metric {

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong totalElapsedTime = new AtomicLong(0);
    private final AtomicLong maxElapsedTime = new AtomicLong(0);

    /** 读 success/failure/total 与 max,并将 max 原子置 0。 */
    public MetricSnapshot getSnapshotAndResetMax() {
        int s = successCount.get();
        int f = failureCount.get();
        long t = totalElapsedTime.get();
        long m = maxElapsedTime.getAndSet(0L);
        return new MetricSnapshot(s, f, t, m);
    }

    /** 上报成功:扣减本批已上报量。 */
    public void deductReported(int success, int failure, long totalElapsed) {
        successCount.getAndAdd(-success);
        failureCount.getAndAdd(-failure);
        totalElapsedTime.getAndAdd(-totalElapsed);
    }

    public long getMaxElapsedTime() {
        return maxElapsedTime.get();
    }

    /** 上报失败:把快照中的 max 与置零后业务新增 max 取大值合并回去。 */
    public void mergeMaxElapsed(long snapshotMaxElapsed) {
        maxElapsedTime.updateAndGet(prev -> Math.max(snapshotMaxElapsed, prev));
    }

    public void updateMaxElapsedTime(long elapsedTime) {
        maxElapsedTime.updateAndGet(prev -> Math.max(elapsedTime, prev));
    }

    public void setMaxElapsedTime(long value) {
        maxElapsedTime.set(value);
    }

    public void incrementSuccess() {
        successCount.incrementAndGet();
    }

    public void incrementFailure() {
        failureCount.incrementAndGet();
    }

    public void addElapsedTime(long time) {
        totalElapsedTime.getAndAdd(time);
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public long getTotalElapsedTime() {
        return totalElapsedTime.get();
    }

    @Override
    public String toString() {
        return "Metric{" +
                "successCount=" + successCount +
                ", failureCount=" + failureCount +
                ", totalElapsedTime=" + totalElapsedTime +
                ", maxElapsedTime=" + maxElapsedTime +
                '}';
    }
}
