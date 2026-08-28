package com.minitelemetry.sdk.instrumentation.servlet;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.context.Scope;
import com.minitelemetry.sdk.propagation.TextMapPropagator;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.SpanContext;
import com.minitelemetry.sdk.trace.SpanKind;
import com.minitelemetry.sdk.trace.StatusCode;
import com.minitelemetry.sdk.trace.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 入站 HTTP 埋点:从请求头 extract 上游上下文,并为本次请求开一个 {@link SpanKind#SERVER} span。
 *
 * <p>上游带合法 {@code traceparent} 时本 span 成为远端父的本地子树根(沿用上游 traceId);
 * 无上游或 header 畸形时降级为新 trace root。两种情况对业务均透明。
 *
 * <p>继承 {@link OncePerRequestFilter},保证一次 HTTP 请求只产生一个 SERVER span
 * ({@code FORWARD}/{@code ERROR} dispatch 不重复埋点)。
 *
 * <p>埋点失败绝不影响业务:extract 与 span 创建包在防御逻辑内,异常只记 debug 日志,请求照常放行。
 */
public class ServletTracingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServletTracingFilter.class);

    private static final String HTTP_REQUEST_METHOD = "http.request.method";
    private static final String URL_PATH = "url.path";
    private static final String HTTP_RESPONSE_STATUS_CODE = "http.response.status_code";

    /** 不创建 SERVER span 的路径,Ant 风格。同时匹配 requestURI 与 servletPath。 */
    public static final List<String> DEFAULT_EXCLUDE_PATHS =
            List.of("/health", "/actuator/**", "/error");

    private final TextMapPropagator propagator;
    private final List<String> excludePaths;
    private final boolean trustIncoming;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 生产默认:排除健康检查与错误页,并信任上游 sampled 位。
     *
     * @param propagator 入站 extract 使用的传播器
     */
    public ServletTracingFilter(TextMapPropagator propagator) {
        this(propagator, DEFAULT_EXCLUDE_PATHS, true);
    }

    /**
     * @param propagator 入站 extract 使用的传播器
     * @param excludePaths 不创建 SERVER span 的路径(Ant 风格),同时匹配 requestURI 与 servletPath
     * @param trustIncoming {@code true} 继承上游 sampled 位;{@code false} 只续接 traceId/parent
     */
    public ServletTracingFilter(
            TextMapPropagator propagator,
            List<String> excludePaths,
            boolean trustIncoming
    ) {
        this.propagator = propagator;
        this.excludePaths = copyExcludePaths(excludePaths);
        this.trustIncoming = trustIncoming;
    }

    private static List<String> copyExcludePaths(List<String> excludePaths) {
        if (excludePaths == null || excludePaths.isEmpty()) {
            return List.of();
        }
        return excludePaths.stream()
                .filter(p -> p != null && !p.isEmpty())
                .toList();
    }

    /**
     * 命中 {@code exclude-paths} 时整次请求不进 {@link #doFilterInternal},不创建 SERVER span。
     * 匹配失败时按未排除处理,避免把业务请求丢掉。
     */
    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        try {
            return isExcluded(request.getRequestURI(), request.getServletPath());
        } catch (RuntimeException e) {
            log.debug("exclude-paths match failed, treating as not excluded", e);
            return false;
        }
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain chain
    ) throws ServletException, IOException {
        Span span = startServerSpan(request);
        if (span == null) {
            chain.doFilter(request, response);
            return;
        }

        try (Scope ignored = span.makeCurrent()) {
            chain.doFilter(request, response);
            recordResponse(span, response);
        } catch (IOException | ServletException | RuntimeException e) {
            // 业务异常记录进 span 后原样抛出,交给容器既有错误处理链
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * extract 上游上下文并创建 SERVER span。
     *
     * @param request 入站请求
     * @return 新建的 SERVER span;埋点自身异常时返回 {@code null},表示本次请求放弃埋点
     */
    private Span startServerSpan(HttpServletRequest request) {
        try {
            Context extracted = applyTrustIncoming(
                    propagator.extract(Context.root(), request, HttpServletRequest::getHeader),
                    trustIncoming);
            Span span = Tracer.spanBuilder(resolveSpanName(request))
                    .setParent(extracted)
                    .setSpanKind(SpanKind.SERVER)
                    // HTTP 入站在语义上即本服务的 local root,显式声明避免依赖自动判定
                    .markAsLocalRoot()
                    .startSpan();
            span.setAttribute(HTTP_REQUEST_METHOD, request.getMethod());
            span.setAttribute(URL_PATH, request.getRequestURI());
            return span;
        } catch (RuntimeException e) {
            log.debug("server span start failed, skipping instrumentation", e);
            return null;
        }
    }

    /**
     * 命中任一 Ant 模式则排除。同时看 requestURI(含 context-path)和 servletPath(去 context-path),
     * 这样 {@code /actuator/**} 在带 context-path 的应用里仍然生效。
     */
    boolean isExcluded(String requestUri, String servletPath) {
        if (excludePaths.isEmpty()) {
            return false;
        }
        for (String pattern : excludePaths) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (pathMatches(pattern, requestUri) || pathMatches(pattern, servletPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean pathMatches(String pattern, String path) {
        return path != null && pathMatcher.match(pattern, path);
    }

    /**
     * {@code trustIncoming=false} 时丢掉上游 sampled 位,只保留 traceId 与 parent spanId,
     * 防止外部伪造 {@code traceparent} flags=01 把本服务打满采样。
     * 已是未来样本或没有 remote parent 时原样返回。
     */
    static Context applyTrustIncoming(Context extracted, boolean trustIncoming) {
        if (trustIncoming || extracted == null) {
            return extracted;
        }
        Span span = Span.fromContext(extracted);
        if (span == null) {
            return extracted;
        }
        SpanContext remote = span.getSpanContext();
        if (!remote.isRemote() || !remote.isSampled()) {
            return extracted;
        }
        return Span.wrap(SpanContext.createRemote(remote.getTraceId(), remote.getSpanId(), false))
                .storeInContext(extracted);
    }

    private String resolveSpanName(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    /** 记录响应状态码,5xx 判为服务端错误。 */
    private void recordResponse(Span span, HttpServletResponse response) {
        try {
            int status = response.getStatus();
            span.setAttribute(HTTP_RESPONSE_STATUS_CODE, status);
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR, "HTTP " + status);
            } else if (span.getStatusCode() == StatusCode.UNSET) {
                span.setStatus(StatusCode.OK);
            }
        } catch (RuntimeException e) {
            log.debug("record response status failed", e);
        }
    }
}
