package com.minitelemetry.sdk.autoconfigure;

import com.minitelemetry.sdk.instrumentation.traced.TracedAspect;
import com.minitelemetry.sdk.metric.export.MetricExporter;
import com.minitelemetry.sdk.metric.export.SpanMetricProcessor;
import com.minitelemetry.sdk.resource.ServiceResource;
import com.minitelemetry.sdk.resource.SpringServiceResource;
import com.minitelemetry.sdk.trace.TracerRuntime;
import com.minitelemetry.sdk.trace.export.BatchSpanProcessor;
import com.minitelemetry.sdk.trace.export.HttpSpanExporter;
import com.minitelemetry.sdk.trace.export.SpanExporter;
import com.minitelemetry.sdk.trace.export.SpanProcessor;
import com.minitelemetry.sdk.trace.sampling.Sampler;
import com.minitelemetry.sdk.trace.sampling.strategy.ErrorLimiter;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.List;

/**
 * Trace + Metric 体系自动配置。
 * <p>装配:
 * <ul>
 *   <li>{@link ServiceResource} —— 服务身份与上报基址</li>
 *   <li>Trace 链路 —— {@link SpanExporter} / {@link SpanProcessor} +
 *       {@link SmartInitializingSingleton} 把 sampler/processors/errorLimiter install 到
 *       {@link TracerRuntime}</li>
 *   <li>Metric 链路 —— {@link SpanMetricProcessor} 与 {@link MetricExporter}(自带 30s 周期调度)</li>
 *   <li>{@link OkHttpClient} 与 {@link TracedAspect}</li>
 * </ul>
 * Sampler 由 {@link SamplingAutoConfiguration} 唯一提供(在本类之前装配)。
 */
@AutoConfiguration
public class TraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceResource serviceResource(Environment environment) {
        return new SpringServiceResource(environment);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public SpanProcessor spanProcessor(SpanExporter spanExporter) {
        return new BatchSpanProcessor(spanExporter);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanExporter spanExporter(ServiceResource serviceResource, OkHttpClient httpClient) {
        return new HttpSpanExporter(serviceResource, httpClient);
    }

    /**
     * 等所有 singleton bean 创建完成后 install 到 {@link TracerRuntime},
     * 之后业务侧 {@code Tracer.spanBuilder(...)} 立即可用。
     */
    @Bean
    public SmartInitializingSingleton tracerRuntimeInitializer(
            Sampler sampler,
            List<SpanProcessor> spanProcessors,
            ErrorLimiter errorLimiter) {
        return () -> TracerRuntime.install(sampler, spanProcessors, errorLimiter);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanMetricProcessor spanMetricProcessor() {
        return new SpanMetricProcessor();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricExporter metricExporter(
            ServiceResource serviceResource,
            SpanMetricProcessor spanMetricProcessor,
            OkHttpClient httpClient) {
        return new MetricExporter(serviceResource, spanMetricProcessor, httpClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public OkHttpClient httpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 新版 {@code @Traced} 切面,走 trace.Tracer 完整链路。 */
    @Bean
    @ConditionalOnMissingBean
    public TracedAspect tracedAspect() {
        return new TracedAspect();
    }
}
