package com.minitelemetry.sdk.autoconfigure;

import com.alibaba.fastjson2.JSON;
import com.minitelemetry.sdk.constants.SdkConstants;
import com.minitelemetry.sdk.resource.ServiceResource;
import com.minitelemetry.sdk.trace.sampling.Sampler;
import com.minitelemetry.sdk.trace.sampling.strategy.ErrorLimiter;
import com.minitelemetry.sdk.trace.sampling.strategy.SamplingStrategy;
import com.minitelemetry.sdk.trace.sampling.strategy.SamplingStrategyStorage;
import com.minitelemetry.sdk.trace.sampling.strategy.StrategyBasedSampler;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 采样子系统自动配置：storage、{@link StrategyBasedSampler}、
 * {@link ErrorLimiter} 与远端策略拉取调度器。
 *
 * {@code before = TraceAutoConfiguration} 保证早于 trace 主链路就绪；
 * 拉取失败沿用上次快照，不清空。
 */
@AutoConfiguration(before = TraceAutoConfiguration.class)
public class SamplingAutoConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(SamplingAutoConfiguration.class);

    private static final long POLL_INTERVAL_MINUTES = 1L;

    /**
     * body 指纹，用于“内容无变化 -> DEBUG，变化 -> INFO”的日志静默。
     */
    private static volatile int lastBodyHash;

    @Bean
    @ConditionalOnMissingBean
    public SamplingStrategyStorage samplingStrategyStorage() {
        return new SamplingStrategyStorage();
    }

    @Bean
    @ConditionalOnMissingBean(Sampler.class)
    public StrategyBasedSampler strategyBasedSampler(
            SamplingStrategyStorage storage) {

        return new StrategyBasedSampler(storage);
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorLimiter errorLimiter(
            SamplingStrategyStorage storage) {

        return new ErrorLimiter(storage);
    }

    /**
     * initialDelay=0 即拉，destroyMethod 让容器关闭时优雅停止。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "samplingStrategyScheduler")
    public ScheduledExecutorService samplingStrategyScheduler(
            SamplingStrategyStorage storage,
            ServiceResource resource) {

        OkHttpClient httpClient = new OkHttpClient();

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t =
                            new Thread(
                                    r,
                                    "sampling-strategy-poller"
                            );
                    t.setDaemon(true);
                    return t;
                });

        scheduler.scheduleAtFixedRate(
                () -> refresh(httpClient, storage, resource),
                0,
                POLL_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        return scheduler;
    }

    private static void refresh(
            OkHttpClient httpClient,
            SamplingStrategyStorage storage,
            ServiceResource resource) {

        String reportBaseUrl = resource.getReportBaseUrl();

        if (reportBaseUrl == null || reportBaseUrl.isBlank()) {
            log.debug("trace.report.base-url unset, skipping");
            return;
        }

        try {
            Request req = new Request.Builder()
                    .url(reportBaseUrl
                            + SdkConstants.SAMPLING_STRATEGIES_PATH
                            + "?serviceName="
                            + resource.getServiceName()
                    )
                    .get()
                    .build();

            try (Response resp = httpClient.newCall(req).execute()) {

                if (!resp.isSuccessful() || resp.body() == null) {
                    log.warn(
                            "sampling strategy fetch failed: HTTP {}",
                            resp.code()
                    );
                    return;
                }

                // body 最确保可读一次，同时用于指纹比对和反序列化
                String body = resp.body().string();

                int hash = body.hashCode();
                boolean changed = hash != lastBodyHash;

                /*
                 * fastjson 2:
                 * parseArray(json, Class) 走 ASM 推断构造器参数名，
                 * 可导致解析 SamplingStrategy 没有无参构造器时报错 Bean
                 */
                List<SamplingStrategy> list = JSON.parseArray(
                        JSON.parseObject(body).getJSONArray("strategies").toJSONString(),
                        SamplingStrategy.class
                );

                Map<String, SamplingStrategy> next = new HashMap<>(list.size());

                for (SamplingStrategy strategy : list) {
                    next.put(strategy.getRootSpanName(), strategy);
                }

                storage.update(next);

                if (changed) {
                    log.info("sampling strategies updated, count={}", next.size());
                    lastBodyHash = hash;
                } else {
                    log.debug("sampling strategies unchanged, count={}", next.size());
                }
            }

        } catch (Exception e) {
            log.warn("sampling strategy refresh failed, keeping previous", e);
        }
    }
}
