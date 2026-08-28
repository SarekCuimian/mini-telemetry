package com.minitelemetry.sdk.instrumentation.mq;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.propagation.TextMapPropagator;
import com.minitelemetry.sdk.propagation.TextMapSetter;
import com.minitelemetry.sdk.trace.DeferredScope;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.SpanKind;
import com.minitelemetry.sdk.trace.StatusCode;
import com.minitelemetry.sdk.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.support.AbstractIntegrationMessageBuilder;
import org.springframework.integration.support.MutableMessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.Collection;

/**
 * 出站 MQ 埋点:为每次发送创建 {@link SpanKind#PRODUCER} span,并把 {@code traceparent}
 * 写入 {@link MessageHeaders},便下游消费直接上同一条 trace。业务代码无需改动。
 *
 * <p>默认挂在 {@link #DEFAULT_PATTERN} 匹配的通道上,由 spring-integration 的
 * {@code @GlobalChannelInterceptor} 全局注册。
 *
 * <p>不限制通道类型:注入依赖 {@code preSend} 所在线程的上下文,与通道如何 dispatch 无关。
 * 异步通道上 span 的相闭会退化为入队耗时,链路本身仍然正确。入站的限制不同,
 * 见 {@link ConsumerTracingInterceptor}。
 *
 * <p>消息会被重建以写入 header,但 {@code MessageHeaders.ID} 与 {@code TIMESTAMP} 保持不变。
 *
 * <p>埋点自身的任何异常都不影响消息投递。
 */
public class ProducerTracingInterceptor implements ChannelInterceptor {

    /** 默认匹配的 channel 名模式。带方向后缀的函数式绑定出站通道。 */
    public static final String DEFAULT_PATTERN = "*-out-*";

    private static final Logger log = LoggerFactory.getLogger(ProducerTracingInterceptor.class);

    private static final String MESSAGING_OPERATION = "messaging.operation";
    private static final String MESSAGING_DESTINATION_NAME = "messaging.destination.name";
    /** 自定义属性:原始 channel 名,便于和 binding 配置对账。 */
    private static final String MESSAGING_SPRING_CHANNEL = "messaging.spring.channel";
    /** OTel {@code messaging.operation} span 名用大写 {@code PUBLISH} 对齐 HTTP METHOD。 */
    private static final String OPERATION_PUBLISH = "publish";
    private static final String SPAN_NAME_PREFIX = "PUBLISH ";

    /**
     * 出站载体不是 {@code MessageHeaders} 本身 —— 它 build 完就不可变,只能通过 builder 重建。
     * 因此用方负责 {@code build()}。
     */
    private static final TextMapSetter<AbstractIntegrationMessageBuilder<?>> SETTER =
            (AbstractIntegrationMessageBuilder<?> builder, String key, String value) -> {
                if (builder != null) {
                    builder.setHeader(key, value);
                }
            };

    private final TextMapPropagator propagator;
    private final DestinationNameResolver destinationResolver;
    private final DeferredScope deferred = new DeferredScope();

    /**
     * 生产装配入口。从容器里读 Spring Cloud Stream 的 binding 配置来解析 destination,
     * 使收发两端指向同一逻辑目的地(binding 名两端不同,destination 才相同)。
     */
    public ProducerTracingInterceptor(TextMapPropagator propagator, ApplicationContext context) {
        this(propagator, DestinationNameResolver.fromApplicationContext(context));
    }

    /** 无容器装配:destination 只能按 channel 名去后缀推断。仅供测试与手工装配。 */
    public ProducerTracingInterceptor(TextMapPropagator propagator) {
        this(propagator, new DestinationNameResolver());
    }

    ProducerTracingInterceptor(TextMapPropagator propagator, DestinationNameResolver destinationResolver) {
        this.propagator = propagator;
        this.destinationResolver = destinationResolver;
    }

    /**
     * 开 PRODUCER span -> {@code makeCurrent} -> inject -> 返回重建后的消息。
     *
     * <p><b>绝不抛异常,绝不返回 null。</b>抛异常会导致本拦截器的 {@code afterSendCompletion}
     * 不被调用(Spring 只为 {@code preSend} 成功返回的拦截器触发结束回调),配对断裂;
     * 返回 null 会静默吞掉业务消息。
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        String channelName = DestinationNameResolver.channelName(channel);
        String destination = destinationResolver.destination(message, channel);

        Span span = deferred.open(() -> {
            Span publish = Tracer.spanBuilder(SPAN_NAME_PREFIX + destination)
                    .setSpanKind(SpanKind.PRODUCER)
                    .startSpan();
            publish.setAttribute(MESSAGING_OPERATION, OPERATION_PUBLISH);
            publish.setAttribute(MESSAGING_DESTINATION_NAME, destination);
            publish.setAttribute(MESSAGING_SPRING_CHANNEL, channelName);
            return publish;
        });

        // 避免兜底。inject 抛异常时不能返回 message —— 那会把上游的过期 traceparent 带给下游。
        Message<?> stripped = message;
        try {
            MutableMessageBuilder<?> builder = MutableMessageBuilder.fromMessage(message);
            AbstractIntegrationMessageBuilder<?> carrier = builder;

            // 先清后写。“消费一条消息 -> 处理 -> 再发一条”的场景,业务常用 fromMessage(incoming)
            // 继承消息头。会把上游的 traceparent 一起复制过来。正常路径 inject 会覆盖掉它,
            // 但埋点路径不应让一个消费链过期的 traceparent 带给下游,哪怕出在已结束 span 上的
            // 错误拓扑。与 FeignTracingClient 的处理语义一致。
            Collection<String> staleFields = propagator.fields();
            if (staleFields != null) {
                for (String field : staleFields) {
                    if (field != null) {
                        carrier.removeHeader(field);
                    }
                }
            }

            // 先拿一版已清理的消息,后面任何一步失败都退回它而不是原始消息
            stripped = builder.build();

            if (span != null) {
                // open 之后才 inject:写进 header 的必须是本 PRODUCER span
                propagator.inject(Context.current(), carrier, SETTER);
                return builder.build();
            }
            return stripped;
        } catch (Throwable t) {
            log.debug("mq traceparent inject failed, sending message without traceparent", t);
            return stripped;
        }
    }

    /** 翻牌 -> 关作用域 -> end span。 */
    @Override
    public void afterSendCompletion(
            Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (ex == null && !sent) {
            Span span = deferred.current();
            if (span != null) {
                span.setStatus(StatusCode.ERROR, "message not sent: channel refused it");
            }
        }
        deferred.close(MessagingErrors.unwrap(ex));
    }

    /** 仅供测试配对不变式。 */
    int deferredDepth() {
        return deferred.depth();
    }
}
