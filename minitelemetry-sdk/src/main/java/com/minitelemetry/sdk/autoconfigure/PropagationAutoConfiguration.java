package com.minitelemetry.sdk.autoconfigure;

import com.minitelemetry.sdk.instrumentation.feign.FeignTracingCapability;
import com.minitelemetry.sdk.instrumentation.feign.FeignTracingClient;
import com.minitelemetry.sdk.instrumentation.hutool.HutoolHttpTracingInterceptor;
import com.minitelemetry.sdk.instrumentation.mq.ConsumerTracingInterceptor;
import com.minitelemetry.sdk.instrumentation.mq.ProducerTracingInterceptor;
import com.minitelemetry.sdk.instrumentation.servlet.ServletTracingFilter;
import com.minitelemetry.sdk.propagation.TextMapPropagator;
import com.minitelemetry.sdk.propagation.W3CTraceContextPropagator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.integration.config.GlobalChannelInterceptor;

/**
 * 跨进程传播自动配置:入站 extract 与出站 inject 的埋点装配。
 * 以下各组无 yml 开关,classpath 存在对应库时直接装配:
 *
 * <ul>
 *   <li>TextMapPropagator —— 默认 W3C traceparent,可由业务方同类型 bean 替换</li>
 *   <li>Servlet —— ServletTracingFilter</li>
 *   <li>Feign —— FeignTracingCapability (建 CLIENT span + inject,包装业务 Client)</li>
 *   <li>hutool —— HutoolHttpTracingInterceptor</li>
 * </ul>
 *
 * <p>MQ 是唯一例外,默认关闭,需显式开启:
 * <pre>
 * trace:
 *   mq:
 *     enabled: true
 * </pre>
 * 它靠 channel 名模式匹配决定挂载点,并且会重建业务消息以写入 header,风险性质与上面三组不同,因此保留一个
 * 可在配置中心随时关闭的开关。见 ProducerTracingInterceptor 与 ConsumerTracingInterceptor。
 * 在 TraceAutoConfiguration 之后装配。埋点触发时 TracerRuntime 已 install 完成,故 span 创建链路可用。
 */
@AutoConfiguration(after = TraceAutoConfiguration.class)
public class PropagationAutoConfiguration {

    /** Filter 顺序:{@link Ordered#HIGHEST_PRECEDENCE} + 10,便于业务插更前置的过滤器。 */
    static final int SERVLET_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    /** MQ 埋点开关的配置前缀。{@code trace.mq.enabled=true} 才装配,不配即关。 */
    static final String MQ_PROPERTY_PREFIX = "trace.mq";

    @Bean
    @ConditionalOnMissingBean
    public TextMapPropagator textMapPropagator() {
        return W3CTraceContextPropagator.getInstance();
    }

    /** Servlet 入站埋点。仅 Servlet Web 应用且 spring-web 在 classpath 时装配。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletPropagationConfiguration {

        /**
         * 尽可能靠前执行,使 SERVER span 覆盖后续全部过滤器与业务逻辑的耗时。
         * 排除 {@code /health}、{@code /actuator/**}、{@code /error},并信任上游 sampled 位。
         */
        @Bean
        @ConditionalOnMissingBean(name = "servletPropagationFilterRegistration")
        public FilterRegistrationBean<ServletTracingFilter> servletPropagationFilterRegistration(
                TextMapPropagator propagator) {
            ServletTracingFilter filter = new ServletTracingFilter(propagator);
            FilterRegistrationBean<ServletTracingFilter> registration = new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns("/*");
            registration.setOrder(SERVLET_FILTER_ORDER);
            registration.setName("servletPropagationFilter");
            return registration;
        }
    }

    /**
     * Feign 出站埋点:注册 {@link FeignTracingCapability},通过 Feign 原生 {@code Capability} 扩展点
     * 把业务实际使用的 {@code Client} 包成 {@link FeignTracingClient},承载完整 CLIENT span
     * (创建 -> inject -> 发送 -> 响应翻牌 -> end)。Spring Cloud OpenFeign 会自动应用到全部 Feign client,业务无感。
     *
     * <p>header 注入由 {@link FeignTracingClient} 在 CLIENT span 的上下文下完成,下游 parent 才正确。
     * 不在 {@code RequestInterceptor} 阶段注入 —— 那时尚无 CLIENT span,会让下游 parent 错到上层 SERVER span。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.Capability")
    static class FeignPropagationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public FeignTracingCapability tracingFeignCapability(TextMapPropagator propagator) {
            return new FeignTracingCapability(propagator);
        }
    }

    /**
     * MQ 收发埋点:出站建 PRODUCER span 并把 traceparent 写进 {@code MessageHeaders},
     * 入站从 header 还原上游并建 CONSUMER span。业务代码零改动。
     *
     * <p>通过 spring-integration 的 {@link GlobalChannelInterceptor} 按 channel 名模式全局注册,
     * 默认匹配 Spring Cloud Stream 函数式绑定的 {@code *-out-*} / {@code *-in-*}。
     * 若业务使用了不带方向后缀的裸 destination,需自行覆盖这两个 bean 并调整 patterns。
     *
     * <p>order 取 {@link Ordered#HIGHEST_PRECEDENCE} + 10:{@code GlobalChannelInterceptor}
     * 的负序会排在通道上显式声明的拦截器之前,确保 MQ span 覆盖包含业务拦截器在内的全过程。
     * 与 {@link #SERVLET_FILTER_ORDER} 同一套约定。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.integration.config.GlobalChannelInterceptor")
    @ConditionalOnProperty(prefix = MQ_PROPERTY_PREFIX, name = "enabled", havingValue = "true")
    static class MqPropagationConfiguration {

        /** 与 {@link #SERVLET_FILTER_ORDER} 同义:尽可能靠外,让 span 覆盖全过程。 */
        static final int MQ_INTERCEPTOR_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

        @Bean
        @ConditionalOnMissingBean
        @GlobalChannelInterceptor(patterns = ProducerTracingInterceptor.DEFAULT_PATTERN, order = MQ_INTERCEPTOR_ORDER)
        public ProducerTracingInterceptor producerTracingInterceptor(
                TextMapPropagator propagator, ApplicationContext context) {
            return new ProducerTracingInterceptor(propagator, context);
        }

        @Bean
        @ConditionalOnMissingBean
        @GlobalChannelInterceptor(patterns = ConsumerTracingInterceptor.DEFAULT_PATTERN, order = MQ_INTERCEPTOR_ORDER)
        public ConsumerTracingInterceptor consumerTracingInterceptor(
                TextMapPropagator propagator, ApplicationContext context) {
            return new ConsumerTracingInterceptor(propagator, context);
        }
    }

    /** hutool 出站埋点。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "cn.hutool.http.GlobalInterceptor")
    static class HutoolHttpPropagationConfiguration {

        /**
         * 工厂方法里立刻 {@link HutoolHttpTracingInterceptor#install()}。
         * 时机等同本 bean 的初始化,早于后续业务单例,尽量赶在任何 {@code HttpRequest} 快照之前。
         * 不使用 {@code SmartInitializingSingleton}:那会等到全部单例建完,挂载已经太晚。
         */
        @Bean
        @ConditionalOnMissingBean
        public HutoolHttpTracingInterceptor hutoolPropagationInterceptor(TextMapPropagator propagator) {
            HutoolHttpTracingInterceptor interceptor = new HutoolHttpTracingInterceptor(propagator);
            interceptor.install();
            return interceptor;
        }
    }
}
