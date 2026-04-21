package com.minitelemetry.testing;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.minitelemetry.context.Context;
import com.minitelemetry.context.Scope;
import com.minitelemetry.trace.ReadableSpan;
import com.minitelemetry.trace.Span;
import com.minitelemetry.trace.SpanKind;
import com.minitelemetry.trace.StatusCode;
import com.minitelemetry.trace.Tracer;

public final class MiniTelemetrySelfTest {
    private MiniTelemetrySelfTest() {
    }

    public static void main(String[] args) throws Exception {
        currentContextDefaultsToRootWithoutSpan();
        rootSpanStartsNewTraceAndHasNoParent();
        childSpanInheritsTraceAndParentSpanId();
        explicitParentOverridesCurrentContext();
        capturedContextCanBePropagatedToAnotherThread();
        wrappedCallablePropagatesContextAndReturnsValue();
        wrappedRunnableRestoresWorkerThreadContextAfterExecution();
        outOfOrderScopeCloseDoesNotCorruptCurrentContext();
        recordExceptionSetsErrorStatusAndExceptionAttributes();
        endIsIdempotent();
        attributesAreExposedAsSnapshot();
        System.out.println("MiniTelemetrySelfTest: all checks passed.");
    }

    private static void currentContextDefaultsToRootWithoutSpan() {
        check(Context.current() == Context.root(), "default current context should be root");
        check(Span.current() == null, "default current span should be null");
    }

    private static void rootSpanStartsNewTraceAndHasNoParent() {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);

        Span root = tracer.spanBuilder("root").setSpanKind(SpanKind.SERVER).startSpan();
        root.end();

        ReadableSpan exported = exporter.getSpans().get(0);
        check(exported.getParentSpanId() == null, "root span should not have parentSpanId");
        check(exported.getSpanContext().getTraceId() != null, "root span should have traceId");
        check(exported.getSpanContext().getTraceId().length() == 32, "traceId should be 32 hex chars");
        check(exported.getSpanContext().getSpanId().length() == 16, "spanId should be 16 hex chars");
    }

    private static void childSpanInheritsTraceAndParentSpanId() {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);

        Span parent = tracer.spanBuilder("parent").setSpanKind(SpanKind.SERVER).startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder("child").setSpanKind(SpanKind.INTERNAL).startSpan();
            try (Scope ignoredChild = child.makeCurrent()) {
                child.setStatus(StatusCode.OK, "ok");
            } finally {
                child.end();
            }
        } finally {
            parent.end();
        }

        List<ReadableSpan> spans = exporter.getSpans();
        check(spans.size() == 2, "expected 2 exported spans");

        ReadableSpan exportedChild = spans.get(0);
        ReadableSpan exportedParent = spans.get(1);
        check(
                exportedParent.getSpanContext().getTraceId().equals(exportedChild.getSpanContext().getTraceId()),
                "child traceId should equal parent traceId"
        );
        check(
                exportedParent.getSpanContext().getSpanId().equals(exportedChild.getParentSpanId()),
                "child parentSpanId should equal parent spanId"
        );
    }

    private static void explicitParentOverridesCurrentContext() {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);

        Span outer = tracer.spanBuilder("outer").startSpan();
        Span explicitParent = tracer.spanBuilder("explicit-parent").startSpan();

        try (Scope ignored = outer.makeCurrent()) {
            Span child = tracer.spanBuilder("child")
                    .setParent(Context.root().with(explicitParent))
                    .startSpan();
            child.end();
        } finally {
            outer.end();
            explicitParent.end();
        }

        ReadableSpan child = exporter.getSpans().get(0);
        check(
                explicitParent.getSpanContext().getTraceId().equals(child.getSpanContext().getTraceId()),
                "explicit parent traceId should override current context traceId"
        );
        check(
                explicitParent.getSpanContext().getSpanId().equals(child.getParentSpanId()),
                "explicit parent spanId should override current context parentSpanId"
        );
    }

    private static void capturedContextCanBePropagatedToAnotherThread() throws Exception {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Span parent = tracer.spanBuilder("parent").setSpanKind(SpanKind.SERVER).startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            Context captured = Context.current();
            Future<?> future = executor.submit(captured.wrap(new Runnable() {
                @Override
                public void run() {
                    Span asyncChild = tracer.spanBuilder("async-child").setSpanKind(SpanKind.INTERNAL).startSpan();
                    try (Scope ignoredAsync = asyncChild.makeCurrent()) {
                        asyncChild.setAttribute("async", true);
                        asyncChild.setStatus(StatusCode.OK, "done");
                    } finally {
                        asyncChild.end();
                    }
                }
            }));

            future.get(3, TimeUnit.SECONDS);
        } finally {
            parent.end();
            executor.shutdownNow();
        }

        ReadableSpan child = exporter.getSpans().get(0);
        ReadableSpan exportedParent = exporter.getSpans().get(1);
        check(
                exportedParent.getSpanContext().getTraceId().equals(child.getSpanContext().getTraceId()),
                "async child traceId should equal parent traceId"
        );
        check(
                exportedParent.getSpanContext().getSpanId().equals(child.getParentSpanId()),
                "async child parentSpanId should equal parent spanId"
        );
        check(Boolean.TRUE.equals(child.getAttributes().get("async")), "async attribute should be true");
    }

    private static void wrappedCallablePropagatesContextAndReturnsValue() throws Exception {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Span parent = tracer.spanBuilder("parent").startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            Future<String> future = executor.submit(Context.current().wrap(new java.util.concurrent.Callable<String>() {
                @Override
                public String call() {
                    Span child = tracer.spanBuilder("callable-child").startSpan();
                    try (Scope ignoredChild = child.makeCurrent()) {
                        child.setStatus(StatusCode.OK, "callable");
                        return child.getParentSpanId();
                    } finally {
                        child.end();
                    }
                }
            }));

            String parentSpanId = future.get(3, TimeUnit.SECONDS);
            check(
                    parent.getSpanContext().getSpanId().equals(parentSpanId),
                    "wrapped callable should see captured parent context"
            );
        } finally {
            parent.end();
            executor.shutdownNow();
        }
    }

    private static void wrappedRunnableRestoresWorkerThreadContextAfterExecution() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Span parent = new Tracer("test-tracer", new InMemorySpanExporter()).spanBuilder("parent").startSpan();
            try (Scope ignored = parent.makeCurrent()) {
                Future<?> wrapped = executor.submit(Context.current().wrap(new Runnable() {
                    @Override
                    public void run() {
                        check(Span.current() != null, "wrapped runnable should install captured context");
                    }
                }));
                wrapped.get(3, TimeUnit.SECONDS);
            } finally {
                parent.end();
            }

            Future<Boolean> after = executor.submit(new java.util.concurrent.Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return Span.current() == null;
                }
            });
            check(after.get(3, TimeUnit.SECONDS), "wrapped runnable should restore worker thread context");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void outOfOrderScopeCloseDoesNotCorruptCurrentContext() {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);

        Span outer = tracer.spanBuilder("outer").startSpan();
        Scope outerScope = outer.makeCurrent();

        Span inner = tracer.spanBuilder("inner").startSpan();
        Scope innerScope = inner.makeCurrent();

        outerScope.close();
        check(Span.current() == inner, "closing outer scope first should keep inner current");

        innerScope.close();
        check(Span.current() == outer, "closing inner scope should restore outer context");

        outer.end();
        inner.end();
    }

    private static void recordExceptionSetsErrorStatusAndExceptionAttributes() {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);

        Span span = tracer.spanBuilder("error-span").startSpan();
        IllegalArgumentException exception = new IllegalArgumentException("bad input");
        span.recordException(exception);
        span.end();

        ReadableSpan exported = exporter.getSpans().get(0);
        check(exported.getStatusCode() == StatusCode.ERROR, "recordException should mark span as ERROR");
        check(
                "IllegalArgumentException: bad input".equals(exported.getStatusMessage()),
                "recordException should set status message"
        );
        check(
                IllegalArgumentException.class.getName().equals(exported.getAttributes().get("exception.type")),
                "recordException should set exception.type"
        );
        check(
                "bad input".equals(exported.getAttributes().get("exception.message")),
                "recordException should set exception.message"
        );
    }

    private static void endIsIdempotent() {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);

        Span span = tracer.spanBuilder("once").startSpan();
        span.end();
        span.end();
        span.end();

        check(exporter.getSpans().size() == 1, "end should export span only once");
    }

    private static void attributesAreExposedAsSnapshot() {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        Tracer tracer = new Tracer("test-tracer", exporter);

        Span span = tracer.spanBuilder("snapshot").startSpan();
        span.setAttribute("k1", "v1");
        check("v1".equals(span.getAttributes().get("k1")), "snapshot should expose stored attributes");

        boolean unsupported = false;
        try {
            span.getAttributes().put("k2", "v2");
        } catch (UnsupportedOperationException expected) {
            unsupported = true;
        }

        check(unsupported, "attribute snapshot should be immutable");
        span.end();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
