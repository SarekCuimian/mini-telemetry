package com.minitelemetry.javaagent;

import com.minitelemetry.sdk.annotation.Traced;
import com.minitelemetry.sdk.context.Scope;
import com.minitelemetry.sdk.instrumentation.traced.TracedSpanNameResolver;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.SpanBuilder;
import com.minitelemetry.sdk.trace.StatusCode;
import com.minitelemetry.sdk.trace.Tracer;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

/** 被 Byte Buddy 内联到带 {@link Traced} 的业务方法中的 Advice。 */
public final class TracedAdvice {

    private TracedAdvice() {
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Span onEnter(
            @Advice.Origin Method method,
            @Advice.Local("minitelemetryScope") Scope scope) {
        Traced traced = method.getAnnotation(Traced.class);
        if (traced == null) {
            return null;
        }
        SpanBuilder builder = Tracer.spanBuilder(TracedSpanNameResolver.resolve(traced, method))
                .setSpanKind(traced.kind());
        if (traced.localRoot()) {
            builder.markAsLocalRoot();
        }
        Span span = builder.startSpan();
        scope = span.makeCurrent();
        return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Enter Span span,
            @Advice.Local("minitelemetryScope") Scope scope,
            @Advice.Thrown Throwable throwable) {
        if (span == null) {
            return;
        }
        try {
            if (throwable == null && span.getStatusCode() == StatusCode.UNSET) {
                span.setStatus(StatusCode.OK);
            } else if (throwable != null) {
                span.recordException(throwable);
            }
        } finally {
            if (scope != null) {
                scope.close();
            }
            span.end();
        }
    }
}
