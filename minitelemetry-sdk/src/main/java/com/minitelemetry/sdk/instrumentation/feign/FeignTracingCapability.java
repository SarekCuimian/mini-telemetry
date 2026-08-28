package com.minitelemetry.sdk.instrumentation.feign;

import com.minitelemetry.sdk.propagation.TextMapPropagator;
import feign.Capability;
import feign.Client;

/**
 * Feign {@link Capability} 扩展点:把业务实际使用的 {@link Client} 包装成 {@link FeignTracingClient}。
 *
 * <p>Spring Cloud OpenFeign 构建每个 client 时对其 client 调用 {@link #enrich(Client)},
 * 无论业务是否自定义 client 均覆盖,{@code @FeignClient} 声明无感。仅增强 client 一维，
 * 其余 enrich 重载沿用默认(原样返回)。
 */
public class FeignTracingCapability implements Capability {

    private final TextMapPropagator propagator;

    /** @param propagator 出站 inject 使用的传播器 */
    public FeignTracingCapability(TextMapPropagator propagator) {
        this.propagator = propagator;
    }

    @Override
    public Client enrich(Client client) {
        // 幂等:重复 enrich(多次装配 / 已包装)不再叠加
        if (client instanceof FeignTracingClient) {
            return client;
        }
        return new FeignTracingClient(client, propagator);
    }
}
