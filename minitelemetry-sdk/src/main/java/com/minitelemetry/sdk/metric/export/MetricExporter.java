package com.minitelemetry.sdk.metric.export;

import com.alibaba.fastjson2.JSON;
import com.minitelemetry.sdk.constants.SdkConstants;
import com.minitelemetry.sdk.metric.Metric;
import com.minitelemetry.sdk.metric.MetricSnapshot;
import com.minitelemetry.sdk.resource.ServiceResource;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 周期批量 POST {@link SdkConstants#REPORT_METRICS_PATH};老单条走 {@code /trace/report/reportMetrics}。 */
public class MetricExporter implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MetricExporter.class);
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");
    private static final long EXPORT_INTERVAL_SECONDS = 30L;
    private static final int MAX_BATCH_SIZE = 100;

    private final ServiceResource serviceResource;
    private final SpanMetricProcessor spanMetricProcessor;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    public MetricExporter(ServiceResource serviceResource,
                          SpanMetricProcessor spanMetricProcessor,
                          OkHttpClient httpClient) {
        this.serviceResource = Objects.requireNonNull(serviceResource, "serviceResource");
        this.spanMetricProcessor = Objects.requireNonNull(spanMetricProcessor, "spanMetricProcessor");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metric-exporter");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void afterPropertiesSet() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                export();
            } catch (Throwable t) {
                log.error("scheduled export failed", t);
            }
        }, EXPORT_INTERVAL_SECONDS, EXPORT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }

    public void export() {
        try {
            String reportBaseUrl = serviceResource.getReportBaseUrl();
            if (reportBaseUrl == null || reportBaseUrl.isBlank()) {
                log.error("trace.report.base-url unset, skipping");
                return;
            }

            List<MetricData> pendingMetrics = collectPending(spanMetricProcessor.getMetricKeys());
            if (pendingMetrics.isEmpty()) {
                return;
            }

            String url = reportBaseUrl + SdkConstants.REPORT_METRICS_PATH;
            String serviceName = serviceResource.getServiceName();
            for (int from = 0; from < pendingMetrics.size(); from += MAX_BATCH_SIZE) {
                int to = Math.min(from + MAX_BATCH_SIZE, pendingMetrics.size());
                postBatch(url, serviceName, pendingMetrics.subList(from, to));
            }
        } catch (Exception e) {
            log.error("export failed", e);
        }
    }

    private List<MetricData> collectPending(Set<MetricKey> keys) {
        List<MetricData> pendingMetrics = new ArrayList<>();
        long reportTime = System.currentTimeMillis();
        for (MetricKey key : keys) {
            Metric metric = spanMetricProcessor.getMetric(key);
            if (metric == null) {
                continue;
            }
            MetricSnapshot snap = spanMetricProcessor.getMetricSnapshot(key);
            if (snap == null) {
                continue;
            }
            int success = snap.successCount();
            int failure = snap.failureCount();
            if (success <= 0 && failure <= 0) {
                continue;
            }
            pendingMetrics.add(new MetricData(
                    key.spanName(), key.kind(), success, failure,
                    snap.totalElapsedTime(), reportTime, snap.maxElapsedTime()));
        }
        return pendingMetrics;
    }

    private void postBatch(String url, String serviceName, List<MetricData> slice) {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(buildPayloadJson(serviceName, slice), JSON_MEDIA))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                deductReported(slice);
                log.debug("exported, batchSize={}", slice.size());
            } else {
                log.error("export failed: HTTP {}, batchSize={}", response.code(), slice.size());
                rollbackMaxElapsed(slice);
            }
        } catch (Exception e) {
            log.error("export failed, batchSize={}", slice.size(), e);
            rollbackMaxElapsed(slice);
        }
    }

    private void deductReported(List<MetricData> slice) {
        for (MetricData data : slice) {
            Metric metric = spanMetricProcessor.getMetric(new MetricKey(data.getSpanName(), data.getKind()));
            if (metric != null) {
                metric.deductReported(data.getSuccessCount(), data.getFailureCount(), data.getTotalElapsedTime());
            }
        }
    }

    private void rollbackMaxElapsed(List<MetricData> slice) {
        for (MetricData data : slice) {
            Metric metric = spanMetricProcessor.getMetric(new MetricKey(data.getSpanName(), data.getKind()));
            if (metric != null) {
                metric.mergeMaxElapsed(data.getMaxElapsedTime());
            }
        }
    }

    private static String buildPayloadJson(String serviceName, List<MetricData> metrics) {
        return JSON.toJSONString(Map.of(
                "serviceName", serviceName,
                "metrics", metrics
        ));
    }
}
