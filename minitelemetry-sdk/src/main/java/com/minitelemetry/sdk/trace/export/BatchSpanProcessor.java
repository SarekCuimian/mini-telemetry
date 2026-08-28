package com.minitelemetry.sdk.trace.export;

import com.minitelemetry.sdk.trace.ReadableSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步批量导出处理器:业务线程仅入队,独立 worker 线程批量调 exporter。
 *
 * <p>两条路径:
 * <ul>
 *   <li><b>已采样</b>({@code isSampled=true}):span end 后立即入队,子 Span / Root 互不等待,
 *       异步晚到的子 Span 随时可独立导出。</li>
 *   <li><b>RECORD_ONLY</b>({@code recording && !sampled}):按 traceId 暂存到 {@link #traceBuffer},
 *       等错误翻牌后首个已采样 span end 时 drain 补发,或 Root end 仍未采样整包丢弃。</li>
 * </ul>
 *
 * <p>RECORD_ONLY 的 Root end 后短暂保留 completed 状态:晚到的未采样 child 按
 * {@code sampledAtRoot} 丢弃,避免重新创建永等不到 root 的孤儿 buffer。
 *
 * <p>关键参数:
 * <ul>
 *   <li>queueCapacity — worker 队列容量</li>
 *   <li>maxBatchSize — 单次 export 最多多少</li>
 *   <li>scheduleDelayMillis — 攒不满也每 N ms 发一次,也作为 completed 状态保留窗口</li>
 *   <li>maxTracesInBuffer — RECORD_ONLY 等待中的 trace 上限,超限时新 trace 直接丢</li>
 *   <li>traceBufferTtlMillis — RECORD_ONLY trace 最长存活时间,超时由 worker 清理</li>
 * </ul>
 */
public final class BatchSpanProcessor implements SpanProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchSpanProcessor.class);

    public static final int DEFAULT_QUEUE_CAPACITY = 512;
    public static final int DEFAULT_MAX_BATCH_SIZE = 128;
    public static final long DEFAULT_SCHEDULE_DELAY_MILLIS = 5_000L;
    /** trace buffer 容量上界 - worker queue × 4。 */
    public static final int DEFAULT_MAX_TRACES_IN_BUFFER = DEFAULT_QUEUE_CAPACITY * 4;
    /** 单 trace 在 buffer 最长存活 - 调度间隔 × 6。 */
    public static final long DEFAULT_TRACE_BUFFER_TTL_MILLIS = DEFAULT_SCHEDULE_DELAY_MILLIS * 6;

    private final SpanExporter exporter;
    private final BlockingQueue<ReadableSpan> queue;
    private final int maxBatchSize;
    private final long scheduleDelayMillis;
    private final int maxTracesInBuffer;
    private final long traceBufferTtlMillis;

    /**
     * traceId -> RECORD_ONLY 暂存状态。
     *
     * <p>仅未采样且 recording 的 span 进入此 buffer。已采样 span 不走这里。
     */
    private final ConcurrentMap<String, TraceState> traceBuffer = new ConcurrentHashMap<>();
    private final AtomicInteger activeBufferedTraceCount = new AtomicInteger();
    private final Thread worker;
    private volatile boolean running = true;

    public BatchSpanProcessor(SpanExporter exporter) {
        this(exporter,
                DEFAULT_QUEUE_CAPACITY, DEFAULT_MAX_BATCH_SIZE, DEFAULT_SCHEDULE_DELAY_MILLIS,
                DEFAULT_MAX_TRACES_IN_BUFFER, DEFAULT_TRACE_BUFFER_TTL_MILLIS);
    }

    public BatchSpanProcessor(SpanExporter exporter,
                              int queueCapacity, int maxBatchSize, long scheduleDelayMillis) {
        this(exporter, queueCapacity, maxBatchSize, scheduleDelayMillis,
                DEFAULT_MAX_TRACES_IN_BUFFER, DEFAULT_TRACE_BUFFER_TTL_MILLIS);
    }

    public BatchSpanProcessor(SpanExporter exporter,
                              int queueCapacity, int maxBatchSize, long scheduleDelayMillis,
                              int maxTracesInBuffer, long traceBufferTtlMillis) {
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be > 0");
        if (maxBatchSize <= 0) throw new IllegalArgumentException("maxBatchSize must be > 0");
        if (scheduleDelayMillis <= 0) throw new IllegalArgumentException("scheduleDelayMillis must be > 0");
        if (maxTracesInBuffer <= 0) throw new IllegalArgumentException("maxTracesInBuffer must be > 0");
        if (traceBufferTtlMillis <= 0) throw new IllegalArgumentException("traceBufferTtlMillis must be > 0");

        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.maxBatchSize = maxBatchSize;
        this.scheduleDelayMillis = scheduleDelayMillis;
        this.maxTracesInBuffer = maxTracesInBuffer;
        this.traceBufferTtlMillis = traceBufferTtlMillis;

        this.worker = new Thread(this::run, "batch-span-processor");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void onEnd(ReadableSpan span) {
        if (span.getSpanContext().isSampled()) {
            onSampledEnd(span);
            return;
        }
        if (!span.isRecording()) {
            // DROP:不导出、不进 buffer
            return;
        }
        // RECORD_ONLY。以 localRoot(本进程子树根)而非 isRoot 判定;
        // 跨进程 trace 的下游子树顶端 parentSpanId 非空,若判 isRoot 则 buffer 永远等不到 root end
        if (span.isLocalRoot()) {
            onRecordOnlyRootEnd(span.getSpanContext().getTraceId());
        } else {
            onRecordOnlyChildEnd(span.getSpanContext().getTraceId(), span);
        }
    }

    /** 已采样:立刻入队;若同 trace 仍有 RECORD_ONLY 缓冲(错误翻牌后),一并 drain 补发。 */
    private void onSampledEnd(ReadableSpan span) {
        String traceId = span.getSpanContext().getTraceId();
        for (ReadableSpan buffered : drainBufferedTrace(traceId)) {
            enqueue(buffered);
        }
        enqueue(span);
        if (span.isLocalRoot()) {
            // 采样路径不需要 completed 窗口;清掉可能残留的空状态
            removeTraceStateIfEmpty(traceId);
        }
    }

    /** RECORD_ONLY root end:丢弃已缓冲 sibling,标记 completed,root 自身不上报。 */
    private void onRecordOnlyRootEnd(String traceId) {
        TraceState state = traceBuffer.computeIfAbsent(traceId, k -> {
            activeBufferedTraceCount.incrementAndGet();
            return new TraceState();
        });

        synchronized (state.lock) {
            if (!state.rootEnded) {
                state.rootEnded = true;
                state.sampledAtRoot = false;
                state.completedAtMillis = System.currentTimeMillis();
                activeBufferedTraceCount.decrementAndGet();
            }
            state.bufferedSpans.clear();
        }
    }

    /** RECORD_ONLY child end:Root 未结束则暂存;已 completed 则按 sampledAtRoot 丢弃或补发。 */
    private void onRecordOnlyChildEnd(String traceId, ReadableSpan child) {
        TraceState state = traceBuffer.get(traceId);
        if (state == null) {
            if (activeBufferedTraceCount.get() >= maxTracesInBuffer) {
                log.warn("incomplete-trace buffer full, maxTraces={}, span={}, traceId={}",
                        maxTracesInBuffer, child.getName(), traceId);
                return;
            }
            TraceState created = new TraceState();
            created.bufferedSpans.add(child);
            TraceState previous = traceBuffer.putIfAbsent(traceId, created);
            if (previous == null) {
                activeBufferedTraceCount.incrementAndGet();
                return;
            }
            state = previous;
        }

        boolean shouldEnqueue = false;
        synchronized (state.lock) {
            if (state.rootEnded) {
                // Root 已以 RECORD_ONLY 结束:按冻结决策处理晚到 child
                shouldEnqueue = state.sampledAtRoot;
            } else {
                state.bufferedSpans.add(child);
            }
        }

        if (shouldEnqueue) {
            enqueue(child);
        }
    }

    /** 取出并清空同 trace 的 RECORD_ONLY 缓冲;条目保留给后续 completed/TTL 逻辑。 */
    private List<ReadableSpan> drainBufferedTrace(String traceId) {
        TraceState state = traceBuffer.get(traceId);
        if (state == null) {
            return Collections.emptyList();
        }
        synchronized (state.lock) {
            if (state.bufferedSpans.isEmpty()) {
                return Collections.emptyList();
            }

            List<ReadableSpan> drained = new ArrayList<>(state.bufferedSpans);
            state.bufferedSpans.clear();
            if (!state.rootEnded) {
                // 翻牌补发后不再占用“等待 root”名额
                activeBufferedTraceCount.decrementAndGet();
                state.rootEnded = true;
                state.sampledAtRoot = true;
                state.completedAtMillis = System.currentTimeMillis();
            } else {
                state.sampledAtRoot = true;
            }
            return drained;
        }
    }

    private void removeTraceStateIfEmpty(String traceId) {
        TraceState state = traceBuffer.get(traceId);
        if (state == null) {
            return;
        }
        synchronized (state.lock) {
            if (!state.bufferedSpans.isEmpty()) {
                return;
            }
            // 已采样 root 结束且 buffer 空:可直接移除,晚到 sampled child 走 eager 路径
            traceBuffer.remove(traceId, state);
        }
    }

    private void enqueue(ReadableSpan span) {
        if (!queue.offer(span)) {
            log.warn("export queue full, discarding span={}", span.getName());
        }
    }

    private void run() {
        List<ReadableSpan> batch = new ArrayList<>(maxBatchSize);
        while (running) {
            try {
                ReadableSpan first = queue.poll(scheduleDelayMillis, TimeUnit.MILLISECONDS);
                if (first == null) {
                    flushBatch(batch);
                } else {
                    batch.add(first);
                    queue.drainTo(batch, maxBatchSize - 1);
                    flushBatch(batch);
                }
                cleanExpiredBufferedTraces();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                log.error("worker loop failed", t);
            }
        }

        try {
            queue.drainTo(batch);
            flushBatch(batch);
        } catch (Throwable t) {
            log.error("drain failed", t);
        }
    }

    /** 清理 completed 过期条目,以及等不到 root 的 RECORD_ONLY 孤儿。 */
    private void cleanExpiredBufferedTraces() {
        if (traceBuffer.isEmpty()) return;
        long now = System.currentTimeMillis();
        traceBuffer.entrySet().removeIf((Entry<String, TraceState> e) -> {
            TraceState state = e.getValue();
            synchronized (state.lock) {
                if (state.rootEnded) {
                    return now - state.completedAtMillis > scheduleDelayMillis;
                }

                if (state.bufferedSpans.isEmpty()) {
                    activeBufferedTraceCount.decrementAndGet();
                    return true;
                }
                ReadableSpan firstBuffered = state.bufferedSpans.get(0);
                long ageMs = now - firstBuffered.getStartEpochMillis();
                if (ageMs <= traceBufferTtlMillis) return false;
                log.warn("incomplete trace expired, traceId={}, rootSpan={}, ageMs={}, spanCount={}, sampled={}",
                        e.getKey(), firstBuffered.getLocalRootSpanName(),
                        ageMs, state.bufferedSpans.size(), firstBuffered.getSpanContext().isSampled());
                activeBufferedTraceCount.decrementAndGet();
                return true;
            }
        });
    }

    private void flushBatch(List<ReadableSpan> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            exporter.export(batch);
        } catch (Throwable t) {
            log.error("export failed, batchSize={}", batch.size(), t);
        }
        batch.clear();
    }

    @Override
    public ResultCode flush() {
        // 仅 export 已入队部分;RECORD_ONLY buffer 里的孤儿不强吐
        List<ReadableSpan> batch = new ArrayList<>(queue.size());
        queue.drainTo(batch);
        try {
            ResultCode r1 = batch.isEmpty() ? ResultCode.SUCCESS : exporter.export(batch);
            ResultCode r2 = exporter.flush();
            return (r1 == ResultCode.SUCCESS && r2 == ResultCode.SUCCESS) ? ResultCode.SUCCESS : ResultCode.FAILURE;
        } catch (Throwable t) {
            log.error("flush failed", t);
            return ResultCode.FAILURE;
        }
    }

    @Override
    public ResultCode shutdown() {
        running = false;
        worker.interrupt();
        try {
            worker.join(5_000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            return exporter.shutdown();
        } catch (Throwable t) {
            log.error("shutdown failed", t);
            return ResultCode.FAILURE;
        }
    }

    private static final class TraceState {
        private final Object lock = new Object();
        private final List<ReadableSpan> bufferedSpans = new ArrayList<>();
        private boolean rootEnded;
        private boolean sampledAtRoot;
        private long completedAtMillis;
    }
}
