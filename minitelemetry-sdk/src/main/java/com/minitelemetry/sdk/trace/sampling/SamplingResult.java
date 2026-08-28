package com.minitelemetry.sdk.trace.sampling;

import java.util.Objects;

/**
 * 采样决策。Recording 与 Sampled 两位独立:
 * <ul>
 *   <li>{@link Decision#DROP} —— 不记录,不导出。</li>
 *   <li>{@link Decision#RECORD_ONLY} —— 本进程记录(写 attribute、过本地 processor),不导出、不跨进程透传。</li>
 *   <li>{@link Decision#RECORD_AND_SAMPLE} —— 完整记录 + 导出 + traceparent 携带 sampled=1。</li>
 * </ul>
 * <p>不可变。通过 {@link #drop()} / {@link #recordOnly()} / {@link #recordAndSample()} 获取共享单例。
 */
public final class SamplingResult {

    public enum Decision {
        DROP,
        RECORD_ONLY,
        RECORD_AND_SAMPLE
    }

    private static final SamplingResult DROP = new SamplingResult(Decision.DROP);
    private static final SamplingResult RECORD_ONLY = new SamplingResult(Decision.RECORD_ONLY);
    private static final SamplingResult RECORD_AND_SAMPLE = new SamplingResult(Decision.RECORD_AND_SAMPLE);

    private final Decision decision;

    private SamplingResult(Decision decision) {
        this.decision = Objects.requireNonNull(decision, "decision");
    }

    public static SamplingResult drop() { return DROP; }

    public static SamplingResult recordOnly() { return RECORD_ONLY; }

    public static SamplingResult recordAndSample() { return RECORD_AND_SAMPLE; }

    public Decision getDecision() { return decision; }

    public boolean isRecording() { return decision != Decision.DROP; }

    public boolean isSampled() { return decision == Decision.RECORD_AND_SAMPLE; }
}
