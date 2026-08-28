package com.minitelemetry.sdk.resource;

import com.minitelemetry.sdk.constants.SdkConstants;

/**
 * 服务级身份属性 —— 上报目标提供者,对齐 OTel {@code Resource} 概念。
 * <p>当前同时提供 {@code service.name} 与上报基址;二者职责不同,上报基址后续可再拆出。
 */
public interface ServiceResource {

    /** OTel {@code service.name},未配置时返回 {@link SdkConstants#DEFAULT_SERVICE_NAME}。 */
    String getServiceName();

    /**
     * 上报基址 HTTP base URL(不含 path),未配置返回 {@code null} 由调用方降级。
     * 例: {@code http://antigravity-trace-service:80}
     */
    String getReportBaseUrl();
}
