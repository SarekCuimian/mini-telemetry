package com.minitelemetry.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.minitelemetry.api.MiniTelemetry;
import com.minitelemetry.context.Context;
import com.minitelemetry.context.Scope;
import com.minitelemetry.exporter.LoggingSpanExporter;
import com.minitelemetry.trace.Span;
import com.minitelemetry.trace.SpanKind;
import com.minitelemetry.trace.StatusCode;
import com.minitelemetry.trace.Tracer;

public final class MiniTelemetryDemo {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final MiniTelemetry OPEN_TELEMETRY = MiniTelemetry.builder()
            .setSpanExporter(new LoggingSpanExporter())
            .build();
    private static final Tracer TRACER = OPEN_TELEMETRY.getTracer("demo-tracer");

    private MiniTelemetryDemo() {
    }

    public static void main(String[] args) throws Exception {
        printScenario("1. HTTP request with nested spans and fan-out async tasks");
        httpEntry();

        printScenario("2. Explicit parent overrides current context");
        explicitParentScenario();

        printScenario("3. Failure flow with exception recording");
        failureScenario();

        printScenario("4. Fire-and-forget background task with manual context capture");
        detachedBackgroundScenario();

        EXECUTOR.shutdown();
        EXECUTOR.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void httpEntry() throws Exception {
        Span serverSpan = TRACER.spanBuilder("HTTP GET /users/123/profile")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try (Scope ignored = serverSpan.makeCurrent()) {
            serverSpan.setAttribute("http.method", "GET");
            serverSpan.setAttribute("http.route", "/users/123/profile");
            serverSpan.setAttribute("request.id", "req-1001");
            authenticateUser();
            userBiz();
            serverSpan.setStatus(StatusCode.OK, "success");
        } catch (Exception e) {
            serverSpan.recordException(e);
            throw e;
        } finally {
            serverSpan.end();
        }
    }

    private static void userBiz() throws Exception {
        Span bizSpan = TRACER.spanBuilder("userBiz")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (Scope ignored = bizSpan.makeCurrent()) {
            bizSpan.setAttribute("user.id", "123456");
            loadFromCache();
            childMethod();
            callDb();
            runParallelTasks();
            Thread.sleep(100);
            bizSpan.setStatus(StatusCode.OK, "biz done");
        } catch (Exception e) {
            bizSpan.recordException(e);
            throw e;
        } finally {
            bizSpan.end();
        }
    }

    private static void childMethod() throws Exception {
        Span child = TRACER.spanBuilder("childMethod")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (Scope ignored = child.makeCurrent()) {
            child.setAttribute("user.type", "vip");
            Thread.sleep(50);
            child.setStatus(StatusCode.OK, "child ok");
        } finally {
            child.end();
        }
    }

    private static void loadFromCache() throws Exception {
        Span cacheSpan = TRACER.spanBuilder("GET redis:user-profile")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        try (Scope ignored = cacheSpan.makeCurrent()) {
            cacheSpan.setAttribute("cache.system", "redis");
            cacheSpan.setAttribute("cache.hit", false);
            Thread.sleep(25);
            cacheSpan.setStatus(StatusCode.OK, "cache miss");
        } finally {
            cacheSpan.end();
        }
    }

    private static void callDb() throws Exception {
        Span dbSpan = TRACER.spanBuilder("SELECT user")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        try (Scope ignored = dbSpan.makeCurrent()) {
            dbSpan.setAttribute("db.system", "mysql");
            dbSpan.setAttribute("db.statement", "select * from user where id = 123456");
            Thread.sleep(80);
            dbSpan.setStatus(StatusCode.OK, "db ok");
        } finally {
            dbSpan.end();
        }
    }

    private static void authenticateUser() throws Exception {
        Span authSpan = TRACER.spanBuilder("authCheck")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (Scope ignored = authSpan.makeCurrent()) {
            authSpan.setAttribute("auth.type", "bearer");
            authSpan.setAttribute("tenant", "acme");
            Thread.sleep(30);
            authSpan.setStatus(StatusCode.OK, "auth ok");
        } finally {
            authSpan.end();
        }
    }

    private static void runParallelTasks() throws Exception {
        // 先捕获父 Context，再把它包装进线程池任务，子线程里的 Span 才能挂到同一条 trace 上。
        Context parentContext = Context.current();
        List<Future<?>> futures = new ArrayList<Future<?>>();

        futures.add(EXECUTOR.submit(parentContext.wrap(new Runnable() {
            @Override
            public void run() {
                Span asyncSpan = TRACER.spanBuilder("recommendationTask")
                        .setSpanKind(SpanKind.INTERNAL)
                        .startSpan();

                try (Scope ignored = asyncSpan.makeCurrent()) {
                    asyncSpan.setAttribute("async", true);
                    asyncSpan.setAttribute("task.type", "recommendation");
                    sleepSilently(120);
                    asyncSpan.setStatus(StatusCode.OK, "recommendation ok");
                } catch (Exception e) {
                    asyncSpan.recordException(e);
                } finally {
                    asyncSpan.end();
                }
            }
        })));

        futures.add(EXECUTOR.submit(parentContext.wrap(new Runnable() {
            @Override
            public void run() {
                Span asyncSpan = TRACER.spanBuilder("auditLogTask")
                        .setSpanKind(SpanKind.INTERNAL)
                        .startSpan();

                try (Scope ignored = asyncSpan.makeCurrent()) {
                    asyncSpan.setAttribute("async", true);
                    asyncSpan.setAttribute("task.type", "audit");
                    sleepSilently(60);
                    asyncSpan.setStatus(StatusCode.OK, "audit ok");
                } catch (Exception e) {
                    asyncSpan.recordException(e);
                } finally {
                    asyncSpan.end();
                }
            }
        })));

        waitAll(futures);
    }

    private static void explicitParentScenario() throws Exception {
        Span currentSpan = TRACER.spanBuilder("current-request")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        Span explicitParent = TRACER.spanBuilder("message-consume")
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan();

        try (Scope ignored = currentSpan.makeCurrent()) {
            // 这里故意覆盖当前线程里的活动 Span，验证显式 parent 的优先级更高。
            Span child = TRACER.spanBuilder("inventory-check")
                    .setParent(explicitParent.storeInContext(Context.root()))
                    .setSpanKind(SpanKind.CLIENT)
                    .startSpan();

            try (Scope ignoredChild = child.makeCurrent()) {
                child.setAttribute("channel", "kafka");
                child.setAttribute("message.key", "order-9001");
                Thread.sleep(40);
                child.setStatus(StatusCode.OK, "explicit parent applied");
            } finally {
                child.end();
            }
        } finally {
            currentSpan.end();
            explicitParent.end();
        }
    }

    private static void failureScenario() throws Exception {
        Span serverSpan = TRACER.spanBuilder("HTTP POST /checkout")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try (Scope ignored = serverSpan.makeCurrent()) {
            serverSpan.setAttribute("http.method", "POST");
            serverSpan.setAttribute("http.route", "/checkout");
            reserveInventory();
            chargePayment();
            serverSpan.setStatus(StatusCode.OK, "checkout success");
        } catch (Exception e) {
            serverSpan.recordException(e);
            serverSpan.setAttribute("error.handled", true);
        } finally {
            serverSpan.end();
        }
    }

    private static void reserveInventory() throws Exception {
        Span span = TRACER.spanBuilder("reserveInventory")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("sku.count", 2L);
            Thread.sleep(35);
            span.setStatus(StatusCode.OK, "inventory reserved");
        } finally {
            span.end();
        }
    }

    private static void chargePayment() throws Exception {
        Span span = TRACER.spanBuilder("POST payment-gateway")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("payment.provider", "mockpay");
            span.setAttribute("payment.amount", 299L);
            Thread.sleep(45);
            throw new IllegalStateException("gateway timeout");
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private static void detachedBackgroundScenario() throws Exception {
        Span requestSpan = TRACER.spanBuilder("HTTP POST /report/export")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try (Scope ignored = requestSpan.makeCurrent()) {
            requestSpan.setAttribute("job.type", "export");
            dispatchBackgroundJob("report-20260422");
            Thread.sleep(20);
            requestSpan.setStatus(StatusCode.OK, "job accepted");
        } finally {
            requestSpan.end();
        }
    }

    private static void dispatchBackgroundJob(final String jobId) throws Exception {
        // fire-and-forget 场景同样需要先抓取 Context，否则子线程看不到当前请求链路。
        final Context captured = Context.current();
        Future<?> future = EXECUTOR.submit(captured.wrap(new Runnable() {
            @Override
            public void run() {
                Span span = TRACER.spanBuilder("backgroundExport")
                        .setSpanKind(SpanKind.PRODUCER)
                        .startSpan();

                try (Scope ignored = span.makeCurrent()) {
                    span.setAttribute("job.id", jobId);
                    span.setAttribute("job.mode", "fire-and-forget");
                    sleepSilently(90);
                    span.setStatus(StatusCode.OK, "background export submitted");
                } catch (Exception e) {
                    span.recordException(e);
                } finally {
                    span.end();
                }
            }
        }));

        future.get(3, TimeUnit.SECONDS);
    }

    private static void waitAll(List<Future<?>> futures) throws Exception {
        for (Future<?> future : futures) {
            future.get(3, TimeUnit.SECONDS);
        }
    }

    private static void printScenario(String title) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(title);
        System.out.println("==================================================");
    }

    private static void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
