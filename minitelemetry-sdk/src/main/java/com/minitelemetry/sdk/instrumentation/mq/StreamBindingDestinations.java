package com.minitelemetry.sdk.instrumentation.mq;

import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

/**
 * 从 Spring Cloud Stream 的 binding 配置读 {@code destination},不把 stream 打进编译依赖。
 *
 * <p>只用 {@code getBindings().get(name).getDestination()},不调 {@code getBindingDestination};
 * 后者会 {@code bindIfNecessary} 给未知名字创建 binding,拦截器里不能有这个副作用。
 */
final class StreamBindingDestinations {

    private static final String PROPERTIES_CLASS =
            "org.springframework.cloud.stream.config.BindingServiceProperties";

    private StreamBindingDestinations() {
    }

    /** classpath 没有 Stream 或容器里没有该 bean 时返回 {@code null}。 */
    static Function<String, String> tryCreate(ApplicationContext context) {
        if (context == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName(PROPERTIES_CLASS);
            Object properties = context.getBeanProvider(type).getIfAvailable();
            if (properties == null) {
                return null;
            }
            Method getBindings = type.getMethod("getBindings");
            return bindingName -> lookup(properties, getBindings, bindingName);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }

    private static String lookup(Object properties, Method getBindings, String bindingName) {
        if (bindingName == null || bindingName.isBlank()) {
            return null;
        }
        try {
            Object bindings = getBindings.invoke(properties);
            if (!(bindings instanceof Map<?, ?> map)) {
                return null;
            }
            Object binding = map.get(bindingName);
            if (binding == null) {
                return null;
            }
            Object destination = binding.getClass().getMethod("getDestination").invoke(binding);
            if (destination instanceof String text && !text.isBlank()) {
                return text;
            }
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
