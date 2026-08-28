package com.minitelemetry.sdk.constants;

/** SDK 内部常量:上报路径、Spring property key 等。 */
public class SdkConstants {

    public static final String DEFAULT_SERVICE_NAME = "unknown-service";
    public static final String SPRING_APP_NAME_KEY = "spring.application.name";
    /** 上报基址,HTTP base URL,不含 path。 */
    public static final String REPORT_BASE_URL_KEY = "trace.report.base-url";
    public static final String DEFAULT_REPORT_BASE_URL = "http://antigravity-trace-service:80";

    public static final String REPORT_SPANS_PATH = "/trace/report/spans";
    public static final String REPORT_METRICS_PATH = "/trace/report/metrics";
    public static final String SAMPLING_STRATEGIES_PATH = "/trace/sampling/strategies";
}
