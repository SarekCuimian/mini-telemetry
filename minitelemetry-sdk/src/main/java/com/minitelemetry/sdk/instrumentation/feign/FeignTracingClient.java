package com.minitelemetry.sdk.instrumentation.feign;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.context.Scope;
import com.minitelemetry.sdk.propagation.TextMapPropagator;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.SpanKind;
import com.minitelemetry.sdk.trace.StatusCode;
import com.minitelemetry.sdk.trace.Tracer;
import feign.Client;
import feign.Request;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feign 出站 CLIENT span 埋点:装饰真正的 {@link Client},在 {@link #execute} 内包住整次 HTTP 调用
 * (创建 span -> inject -> 发送 -> 响应翻牌 -> end)。
 *
 * <p>CLIENT span 挂在当前线程已有 span 下写子节点;无当前 span 时自成 root。
 * inject 在 {@code makeCurrent()} 之后执行,确保下游 remote parent 是本 CLIENT span 而非上层 span。
 *
 * <p>埋点自身异常不影响业务调用:建 span 失败即透传原始 {@code execute}。
 */
public class FeignTracingClient implements Client {

    private static final Logger log = LoggerFactory.getLogger(FeignTracingClient.class);

    private static final String HTTP_REQUEST_METHOD = "http.request.method";
    private static final String URL_FULL = "url.full";
    private static final String SERVER_ADDRESS = "server.address";
    private static final String HTTP_RESPONSE_STATUS_CODE = "http.response.status_code";

    private final Client delegate;
    private final TextMapPropagator propagator;

    /**
     * @param delegate 业务实际使用的 Feign Client(被装饰对象),不可为 null
     * @param propagator 出站 inject 使用的传播器
     */
    public FeignTracingClient(Client delegate, TextMapPropagator propagator) {
        this.delegate = delegate;
        this.propagator = propagator;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        Span span = startClientSpan(request);
        if (span == null) {
            // 埋点创建失败:透传原始调用,业务不受影响
            return delegate.execute(request, options);
        }

        try (Scope ignored = span.makeCurrent()) {
            // makeCurrent 之后再 inject:确保写进 header 的是本 CLIENT span 的上下文
            Request injected = injectContext(request);
            Response response = delegate.execute(injected, options);
            recordResponse(span, response);
            return response;
        } catch (IOException | RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * 创建 CLIENT span 并记录请求侧属性。
     *
     * @return 新建的 CLIENT span;埋点自身异常时返回 {@code null},表示本次调用放弃埋点
     */
    private Span startClientSpan(Request request) {
        try {
            Span span = Tracer.spanBuilder(resolveSpanName(request))
                    .setSpanKind(SpanKind.CLIENT)
                    .startSpan();
            span.setAttribute(HTTP_REQUEST_METHOD, request.httpMethod().name());
            span.setAttribute(URL_FULL, request.url());
            String host = resolveHost(request.url());
            if (host != null) {
                span.setAttribute(SERVER_ADDRESS, host);
            }
            return span;
        } catch (RuntimeException e) {
            log.debug("client span start failed, skipping instrumentation", e);
            return null;
        }
    }

    /**
     * 把当前上下文注入请求头。Feign {@link Request} 的 header 不可变,需基于原 request 重建。
     * <p>注入失败(理论上 propagator 契约保证不抛)时兜底返回原 request,不阻断调用。
     */
    private Request injectContext(Request request) {
        try {
            Map<String, Collection<String>> headers = new LinkedHashMap<>(request.headers());
            // 覆盖语义:先清同名 key,避免重试 / 多层拦截叠加出现多个 traceparent
            propagator.inject(Context.current(), headers,
                    (Map<String, Collection<String>> carrier, String key, String value) ->
                            carrier.put(key, List.of(value)));
            return Request.create(
                    request.httpMethod(),
                    request.url(),
                    headers,
                    request.body(),
                    request.charset(),
                    request.requestTemplate());
        } catch (RuntimeException e) {
            log.debug("context inject failed, sending original request", e);
            return request;
        }
    }

    /** 记录响应状态码,5xx 判为服务端错误;非错误且未翻牌则标记 OK。 */
    private void recordResponse(Span span, Response response) {
        try {
            int status = response.status();
            span.setAttribute(HTTP_RESPONSE_STATUS_CODE, status);
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR, "HTTP " + status);
            } else if (span.getStatusCode() == StatusCode.UNSET) {
                span.setStatus(StatusCode.OK);
            }
        } catch (RuntimeException e) {
            log.debug("response status failed", e);
        }
    }

    /**
     * span 名取 {@code "METHOD target"};target 优先用 {@code @FeignClient} 名(低基数),取不到退化为 host。
     * <p>不用完整 URL,避免路径变量把 span 名基数打爆(CLIENT span 作 root 时按该 key 查限流策略)。
     */
    private String resolveSpanName(Request request) {
        String method = request.httpMethod().name();
        String target = resolveTargetName(request);
        return target != null ? method + " " + target : method;
    }

    private String resolveTargetName(Request request) {
        try {
            if (request.requestTemplate() != null && request.requestTemplate().feignTarget() != null) {
                String name = request.requestTemplate().feignTarget().name();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (RuntimeException ignored) {
            // 拿不到 target 元信息,退化到 host
        }
        return resolveHost(request.url());
    }

    private String resolveHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
