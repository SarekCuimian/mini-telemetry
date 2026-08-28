package com.minitelemetry.sdk.trace;

import com.minitelemetry.sdk.trace.export.ResultCode;
import com.minitelemetry.sdk.trace.export.SpanProcessor;
import com.minitelemetry.sdk.trace.sampling.Sampler;
import com.minitelemetry.sdk.trace.sampling.strategy.ErrorLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trace SDK 全局运行时,持有 sampler / processor / errorLimiter,暴露 install/shutdown 生命周期。
 * <p>仅 SDK 初始化(AutoConfig)与测试场景使用,业务代码请直接走 {@link Tracer#spanBuilder(String)}。
 */
public final class TracerRuntime {

    private static final Logger log = LoggerFactory.getLogger(TracerRuntime.class);

    private static volatile Sampler SAMPLER = Sampler.defaultSampler();
    private static volatile List<SpanProcessor> PROCESSORS = Collections.emptyList();
    /** 错误翻牌限流器,可空。空时 ERROR 不翻牌(保守 drop),不翻牌。 */
    private static volatile ErrorLimiter ERROR_LIMITER;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private TracerRuntime() {
    }

    /** 安装全局 sampler / processors / errorLimiter。允许重复调用,会覆盖旧值并打 WARN。 */
    public static void install(Sampler sampler,
                               List<SpanProcessor> processors,
                               ErrorLimiter errorLimiter) {
        Objects.requireNonNull(processors, "processors");
        Objects.requireNonNull(sampler, "sampler");

        boolean firstTime = INSTALLED.compareAndSet(false, true);
        SAMPLER = sampler;
        PROCESSORS = List.copyOf(processors);
        ERROR_LIMITER = errorLimiter;

        String limiterTag = errorLimiter == null ? "<none>" : errorLimiter.getClass().getSimpleName();
        if (firstTime) {
            log.info("installed: sampler={}, processors={}, errorLimiter={}",
                    sampler.getDescription(), PROCESSORS.size(), limiterTag);
        } else {
            log.warn("install() called multiple times, replaced (sampler={}, processors={}, errorLimiter={})",
                    sampler.getDescription(), PROCESSORS.size(), limiterTag);
        }
    }

    /** 通知所有 processor 关闭并重置兜底值。仅非 Spring 场景使用。 */
    public static ResultCode shutdown() {
        ResultCode r = ResultCode.SUCCESS;
        for (SpanProcessor p : PROCESSORS) {
            try {
                if (p.shutdown() == ResultCode.FAILURE) {
                    r = ResultCode.FAILURE;
                }
            } catch (Throwable t) {
                log.error("shutdown failed, processor={}", p.getClass().getName(), t);
                r = ResultCode.FAILURE;
            }
        }
        PROCESSORS = Collections.emptyList();
        SAMPLER = Sampler.alwaysOff();
        ERROR_LIMITER = null;
        INSTALLED.set(false);
        return r;
    }

    /** 通知所有 processor 强制 flush。 */
    public static ResultCode forceFlush() {
        ResultCode r = ResultCode.SUCCESS;
        for (SpanProcessor p : PROCESSORS) {
            if (p.flush() == ResultCode.FAILURE) {
                r = ResultCode.FAILURE;
            }
        }
        return r;
    }

    static boolean isInstalled() { return INSTALLED.get(); }
    static Sampler getSampler() { return SAMPLER; }
    static List<SpanProcessor> getProcessors() { return PROCESSORS; }
    static ErrorLimiter getErrorLimiter() { return ERROR_LIMITER; }
}
