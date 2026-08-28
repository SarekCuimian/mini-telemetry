package com.minitelemetry.sdk.instrumentation.hutool;

import cn.hutool.http.GlobalInterceptor;
import cn.hutool.http.HttpInterceptor;
import cn.hutool.http.HttpRequest;
import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.propagation.TextMapPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * hutool 出站埋点:把当前 trace 上下文注入请求头。
 *
 * <p>hutool 不从 Spring 收集拦截器,必须把<b>本实例</b>挂进 {@link GlobalInterceptor} 全局链,
 * 才能覆盖全进程基于 hutool 的 HTTP 调用(含业务自建的 HttpUtil 门面)。
 *
 * <p>Spring 场景由自动配置创建 bean 时调用 {@link #install()};非 Spring 走
 * {@link #installGlobally(TextMapPropagator)}。
 *
 * <p>当前无条件注入;注入范围收窄策略待定,届时只需改 {@link #process},不影响装配。
 */
public class HutoolHttpTracingInterceptor implements HttpInterceptor<HttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(HutoolHttpTracingInterceptor.class);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private final TextMapPropagator propagator;

    public HutoolHttpTracingInterceptor(TextMapPropagator propagator) {
        this.propagator = propagator;
    }

    /**
     * 非 Spring 入口:构造并挂链,返回同一实例。须在任何 {@code HttpRequest} 创建之前调用。
     *
     * @param propagator 出站 inject 使用的传播器
     * @return 已尝试挂链的拦截器
     */
    public static HutoolHttpTracingInterceptor installGlobally(TextMapPropagator propagator) {
        HutoolHttpTracingInterceptor interceptor = new HutoolHttpTracingInterceptor(propagator);
        interceptor.install();
        return interceptor;
    }

    /**
     * 幂等地把<b>本实例</b>装进 hutool 进程级全局链。
     *
     * <p>hutool 的 {@code HttpConfig} 在构造时一次性快照全局链,故须尽早调用。
     * {@code GlobalInterceptor} 是进程级单例且 add 不去重,用静态标志保证全进程只挂一次
     * (多 ApplicationContext、上下文 refresh 同样安全)。
     *
     * @return 本次调用是否真正执行了安装
     */
    public boolean install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            log.debug("hutool global interceptor already installed, skipping");
            return false;
        }
        GlobalInterceptor.INSTANCE.addRequestInterceptor(this);
        log.info("hutool global interceptor installed");
        return true;
    }

    @Override
    public void process(HttpRequest request) {
        // header(k, v, true) 为覆盖语义,天然满足 TextMapSetter 契约
        propagator.inject(Context.current(), request,
                (HttpRequest carrier, String key, String value) -> carrier.header(key, value, true));
    }
}
