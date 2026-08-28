package com.minitelemetry.sdk.trace.export;

import com.alibaba.fastjson2.JSON;
import com.minitelemetry.sdk.constants.SdkConstants;
import com.minitelemetry.sdk.resource.ServiceResource;
import com.minitelemetry.sdk.trace.ReadableSpan;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通过 HTTP + JSON 同步上报一批 span,失败仅 WARN 不抛异常。
 * <p>线程安全(字段全 final,OkHttpClient 自身线程安全,ServiceResource 视为不可变)。
 * <p>调度由 {@link BatchSpanProcessor} 负责。trace 数据本就抽样,丢一批可致命,运维通过 5xx/超时告警观察。
 */
public final class HttpSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(HttpSpanExporter.class);
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");

    private final ServiceResource serviceResource;
    private final OkHttpClient httpClient;

    public HttpSpanExporter(ServiceResource serviceResource, OkHttpClient httpClient) {
        this.serviceResource = Objects.requireNonNull(serviceResource, "serviceResource");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public ResultCode export(Collection<ReadableSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return ResultCode.SUCCESS;
        }
        String reportBaseUrl = serviceResource.getReportBaseUrl();
        if (reportBaseUrl == null || reportBaseUrl.isBlank()) {
            log.warn("trace.report.base-url unset, discarding spans={}", spans.size());
            return ResultCode.FAILURE;
        }

        String body = buildPayloadJson(spans);
        Request request = new Request.Builder()
                .url(reportBaseUrl + SdkConstants.REPORT_SPANS_PATH)
                .post(RequestBody.create(body, JSON_MEDIA))
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (resp.isSuccessful()) {
                return ResultCode.SUCCESS;
            }
            log.warn("export failed: HTTP {}, batchSize={}", resp.code(), spans.size());
            return ResultCode.FAILURE;
        } catch (Throwable t) {
            log.warn("export failed, batchSize={}", spans.size(), t);
            return ResultCode.FAILURE;
        }
    }

    private String buildPayloadJson(Collection<ReadableSpan> spans) {
        List<SpanData> list = new ArrayList<>(spans.size());
        for (ReadableSpan s : spans) {
            list.add(SpanData.from(s));
        }
        return JSON.toJSONString(Map.of(
                "serviceName", serviceResource.getServiceName(),
                "spans", list
        ));
    }
}
