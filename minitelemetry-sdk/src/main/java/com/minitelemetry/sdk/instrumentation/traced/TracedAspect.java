package com.minitelemetry.sdk.instrumentation.traced;

import com.minitelemetry.sdk.annotation.Traced;
import com.minitelemetry.sdk.context.Scope;
import com.minitelemetry.sdk.instrumentation.traced.TracedSpanNameResolver;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.SpanBuilder;
import com.minitelemetry.sdk.trace.StatusCode;
import com.minitelemetry.sdk.trace.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/** {@link Traced} 注解的 AOP 织入, 创建完整 Trace Span 并传播当前 Context。 */
@Aspect
public class TracedAspect {

    @Around("@annotation(traced)")
    public Object tracedMethod(ProceedingJoinPoint joinPoint, Traced traced) throws Throwable {
        Method method = resolveMethod(joinPoint);
        SpanBuilder builder = Tracer.spanBuilder(TracedSpanNameResolver.resolve(traced, method))
                .setSpanKind(traced.kind());
        // 注解显式声明为服务入口时, 覆盖 SpanBuilder 的自动判定。
        // 默认 false 时不调用, 保持 SDK 原有行为 (root / remote parent 自动为 local root)。
        if (traced.localRoot()) {
            builder.markAsLocalRoot();
        }
        Span span = builder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            Object result = joinPoint.proceed();
            // 业务代码可能已经显式标记 ERROR;只有未设置状态时才默认标记成功。
            if (span.getStatusCode() == StatusCode.UNSET) {
                span.setStatus(StatusCode.OK);
            }
            return result;
        } catch (Throwable e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * 从代理 target 解析真实 Method。JDK 动态代理下 {@code MethodSignature.getMethod()}
     * 往往是接口方法, 默认 spanName 会落到接口全名;沿 target 类层级找实现方法。
     */
    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        if (target == null) {
            return method;
        }
        Class<?> targetClass = target.getClass();
        while (targetClass != null) {
            try {
                return targetClass.getDeclaredMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                targetClass = targetClass.getSuperclass();
            }
        }
        return method;
    }
}
