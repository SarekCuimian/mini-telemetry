package com.minitelemetry.sdk.resource;

import com.minitelemetry.sdk.constants.SdkConstants;
import org.springframework.core.env.Environment;

/**
 * 从 Spring {@link Environment} 读取服务名/上报基址的 {@link ServiceResource} 实现。
 * <p>构造期一次性读取并固化到 final 字段,getter 直接返回。
 */
public class SpringServiceResource implements ServiceResource {

    private final String serviceName;
    private final String reportBaseUrl;

    public SpringServiceResource(Environment environment) {
        this.serviceName = resolveServiceName(environment);
        this.reportBaseUrl = resolveReportBaseUrl(environment);
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getReportBaseUrl() {
        return reportBaseUrl;
    }

    private static String resolveServiceName(Environment env) {
        if (env != null) {
            String appName = env.getProperty(SdkConstants.SPRING_APP_NAME_KEY);
            if (appName != null && !appName.isBlank()) {
                return appName;
            }
        }
        return SdkConstants.DEFAULT_SERVICE_NAME;
    }

    /** 读 {@code trace.report.base-url};未配则用集群内默认基址。 */
    private static String resolveReportBaseUrl(Environment env) {
        if (env == null) {
            return null;
        }
        String url = env.getProperty(SdkConstants.REPORT_BASE_URL_KEY);
        if (url != null && !url.isBlank()) {
            return url;
        }
        return SdkConstants.DEFAULT_REPORT_BASE_URL;
    }
}
