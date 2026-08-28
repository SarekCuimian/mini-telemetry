package com.minitelemetry.sdk.instrumentation.mq;

import com.minitelemetry.sdk.context.Context;
import com.minitelemetry.sdk.propagation.TextMapGetter;
import com.minitelemetry.sdk.propagation.TextMapPropagator;
import com.minitelemetry.sdk.trace.DeferredScope;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.SpanKind;
import com.minitelemetry.sdk.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.ChannelInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQ 入站埋点:从 {@link MessageHeaders} 还原上游上下文,并为消费创建
 * {@link SpanKind#CONSUMER} span。业务代码零改动。
 *
 * <p>挂在 {@code *-in-*} 通道上(Spring Cloud Stream 函数式绑定的入站命名约定),
 * 由 {@code @GlobalChannelInterceptor} 全局注册。
 *
 * <p>上游带合法 {@code traceparent} 时本 span 成为远端父的本地子树根(沿用上游 traceId
 * 并共享 sampled 决策);无上游或 header 畸形时降级为新 trace root。两种情况对业务均透明。
 *
 * <p><b>只支持同步通道</b>:CONSUMER span 必须覆盖业务 handler 的真实执行区间,
 * 且作用域必须开在执行 handler 的线程上。而 {@code preSend} 跑在调用 {@code send()} 的线程上 ——
 * 两者只在同步 dispatch 的通道上重合。
 *
 * <p>Spring Cloud Stream 的收发通道默认都是 {@code DirectWithAttributesChannel}
 * (见 {@code SubscribableChannelBindingTargetFactory} 与 {@code StreamBridge}),同步 dispatch,
 * 所以这条路径覆盖全部现有绑定。非同步通道直接跳过并告警一次,宁可没数据也不要错数据。
 *
 * <p>埋点失败绝不影响业务:{@code preSend} 整体兜住 {@code Throwable},失败时原样放行消息。
 */
public class ConsumerTracingInterceptor implements ChannelInterceptor {

    /** 默认匹配的 channel 名模式。带方向后缀的函数式绑定入站通道。 */
    public static final String DEFAULT_PATTERN = "*-in-*";

    private static final Logger log = LoggerFactory.getLogger(ConsumerTracingInterceptor.class);

    private static final String MESSAGING_OPERATION = "messaging.operation";
    private static final String MESSAGING_DESTINATION_NAME = "messaging.destination.name";
    /** 自定义属性:原始 channel 名,便于和 binding 配置对账。 */
    private static final String MESSAGING_SPRING_CHANNEL = "messaging.spring.channel";
    /** OTel {@code messaging.operation}:span 名用大写 {@code CONSUME},避免和 kind 的 CONSUMER 及业务 process 混淆。 */
    private static final String OPERATION_PROCESS = "process";
    private static final String SPAN_NAME_PREFIX = "CONSUME ";

    /**
     * 入站读取。不能用方法引用:{@link TextMapGetter#get} 要求返回 {@code String},
     * 而 {@link MessageHeaders#get(Object)} 返回 {@code Object},必须显式收敛。
     *
     * <p>{@code byte[]} 分支是防御 AMQP 长字符串在个别 {@code MessagePropertiesConverter}
     * 配置下不被转成 {@code String} 的情况。
     */
    private static final TextMapGetter<MessageHeaders> GETTER = (MessageHeaders headers, String key) -> {
        if (headers == null || key == null) {
            return null;
        }
        Object value = headers.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    };

    private final TextMapPropagator propagator;
    private final DestinationNameResolver destinationResolver;
    private final DeferredScope deferred = new DeferredScope();
    /** 已告警过的非同步通道,保证每个通道只 WARN 一次;通道数有界。 */
    private final Set<String> warnedAsyncChannels = ConcurrentHashMap.newKeySet();

    /**
     * 生产装配入口。从容器里读 Spring Cloud Stream 的 binding 配置来解析 destination,
     * 使收发两端指向同一逻辑目的地(binding 名两端不同,destination 才相同)。
     */
    public ConsumerTracingInterceptor(TextMapPropagator propagator, ApplicationContext context) {
        this(propagator, DestinationNameResolver.fromApplicationContext(context));
    }

    /** 无容器装配:destination 只能按 channel 名去后缀推断。仅供测试与手工装配。 */
    public ConsumerTracingInterceptor(TextMapPropagator propagator) {
        this(propagator, new DestinationNameResolver());
    }

    ConsumerTracingInterceptor(TextMapPropagator propagator, DestinationNameResolver destinationResolver) {
        this.propagator = propagator;
        this.destinationResolver = destinationResolver;
    }

    /**
     * <b>绝不抛异常,绝不返回 null。</b>抛异常会导致 {@code afterSendCompletion} 不被调用,配对断裂;
     * 返回 null 会静默吞掉业务消息。
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (!dispatchesSynchronously(channel)) {
            String name = DestinationNameResolver.channelName(channel);
            if (warnedAsyncChannels.add(name)) {
                log.warn("mq consumer tracing skipped for channel [{}] of type [{}]: only synchronous "
                                + "DirectChannel is supported; send-side callbacks cannot bracket "
                                + "business execution on this channel",
                        name, channel.getClass().getName());
            }
            return message;
        }

        String channelName = DestinationNameResolver.channelName(channel);
        String destination = destinationResolver.destination(message, channel);

        deferred.open(() -> {
            // 基底用 Context.root() 而不是 Context.current():消费线程来自监听器线程池
            // (concurrency > 1 时有多条),上面可能残留上一条消息的上下文,必须从干净的根开始。
            Context extracted = propagator.extract(
                    Context.root(),
                    message.getHeaders(),
                    GETTER);

            Span consume = Tracer.spanBuilder(SPAN_NAME_PREFIX + destination)
                    .setParent(extracted)
                    .setSpanKind(SpanKind.CONSUMER)
                    // MQ 入站在语义上即本服务的 local root,显式声明避免依赖自动判定
                    .markAsLocalRoot()
                    .startSpan();
            consume.setAttribute(MESSAGING_OPERATION, OPERATION_PROCESS);
            consume.setAttribute(MESSAGING_DESTINATION_NAME, destination);
            consume.setAttribute(MESSAGING_SPRING_CHANNEL, channelName);
            return consume;
        });
        return message;
    }

    @Override
    public void afterSendCompletion(
            Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (!dispatchesSynchronously(channel)) {
            return;
        }
        deferred.close(MessagingErrors.unwrap(ex));
    }

    private static boolean dispatchesSynchronously(MessageChannel channel) {
        return channel instanceof DirectChannel;
    }

    /** 仅供测试配对不变式。 */
    int deferredDepth() {
        return deferred.depth();
    }
}
