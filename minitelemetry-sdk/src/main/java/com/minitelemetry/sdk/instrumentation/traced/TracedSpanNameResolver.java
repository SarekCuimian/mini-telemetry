package com.minitelemetry.sdk.instrumentation.traced;

import com.minitelemetry.sdk.annotation.Traced;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * {@link Traced} spanName 解析器。
 *
 * <p>{@code value} 非空时原样使用;否则回退 {@code 声明类全名.方法名}。
 */
public final class TracedSpanNameResolver {
    private TracedSpanNameResolver() {
    }

    public static String resolve(Traced traced, Method method) {
        Objects.requireNonNull(traced, "traced");
        String configuredName = traced.value();
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName;
        }
        return defaultName(method);
    }

    public static String defaultName(Method method) {
        Objects.requireNonNull(method, "method");
        return method.getDeclaringClass().getName() + "." + method.getName();
    }
}
