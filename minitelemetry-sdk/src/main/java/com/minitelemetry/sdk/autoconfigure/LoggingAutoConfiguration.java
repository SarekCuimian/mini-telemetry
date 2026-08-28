package com.minitelemetry.sdk.autoconfigure;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.logging.MdcContextListener;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 日志链路标识自动配置: 把 {@link MdcContextListener} 注册为进程级上下文监听器,
 * 使业务日志可通过 MDC 输出 traceId/spanId。
 *
 * <p>业务侧需在日志模板中引用对应 key(如 logback {@code %X{traceId}})才会实际显示。
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.slf4j.MDC")
public class LoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MdcContextListener mdcContextListener() {
        return new MdcContextListener();
    }

    /**
     * 在全部单例就绪后注册监听器, 与其他埋点装配保持同一惯用法。
     *
     * @param listener 待注册的监听器
     * @return 启动期执行注册的回调
     */
    @Bean
    public SmartInitializingSingleton mdcContextListenerInstaller(MdcContextListener listener) {
        return () -> Context.setContextListener(listener);
    }
}
