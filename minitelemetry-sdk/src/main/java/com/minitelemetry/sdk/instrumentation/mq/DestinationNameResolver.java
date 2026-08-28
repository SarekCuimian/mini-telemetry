package com.minitelemetry.sdk.instrumentation.mq;

import org.springframework.integration.support.context.NamedComponent;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.AbstractMessageChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 解析 MQ span 用的逻辑 destination,对齐
 * {@code spring.cloud.stream.bindings.<binding>.destination}。
 *
 * <p>优先级:
 * <ol>
 *   <li>消息头里的 broker 目的地(如 {@code kafka_receivedTopic} / {@code amqp_receivedExchange})</li>
 *   <li>Stream binding 配置的 {@code destination}(函数名 channel 也能对上真实 topic/exchange)</li>
 *   <li>channel 名去掉 {@code -out-N}/{@code -in-N}</li>
 * </ol>
 * 收发两端因此指向同一逻辑目的地,例如都叫 {@code order.payed}。
 *
 * <p>不用 routing key:里面常带业务 id,会把 span 名基数打爆。
 *
 * <p>包私有:destination 命名规则是埋点的实现细节,不作为对外扩展点。
 * 业务方若要换命名策略,应替换整个拦截器 bean,而不是替换本类。
 */
final class DestinationNameResolver {

    /** 函数式绑定的方向后缀,{@code -out-0} / {@code -in-1} 等。 */
    private static final Pattern BINDING_SUFFIX = Pattern.compile("-(?:out|in)-\\d+$");

    /**
     * 低基数的 broker 目的地 header。顺序即优先级。
     * 不含 routing key / message key。
     */
    static final String[] BROKER_DESTINATION_HEADERS = {
            "spring.cloud.stream.sendto.destination",
            "kafka_receivedTopic",
            "kafka_topic",
            "amqp_receivedExchange",
            "rocketmq_TOPIC"
    };

    /** 拿不到 channel 名时的兜底,保证 span 名非空且低基数。 */
    static final String UNKNOWN = "unknown";

    /** 缓存上限。超出后仍能正确解析,只是不再缓存,用 CPU 换内存安全。 */
    static final int MAX_CACHE_SIZE = 512;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Function<String, String> bindingDestinationLookup;

    DestinationNameResolver() {
        this(null);
    }

    static DestinationNameResolver fromApplicationContext(ApplicationContext context) {
        return new DestinationNameResolver(StreamBindingDestinations.tryCreate(context));
    }

    /**
     * @param bindingDestinationLookup binding 名 -> yaml {@code destination};没有配置时返回 null。
     *                                 不得调用会副作用创建 binding 的 API。
     */
    DestinationNameResolver(Function<String, String> bindingDestinationLookup) {
        this.bindingDestinationLookup = bindingDestinationLookup;
    }

    /**
     * 取 channel 的 bean 名。
     *
     * <p>优先走 spring-integration 的 {@link NamedComponent#getComponentName()} —— 这正是
     * {@code @GlobalChannelInterceptor(patterns=...)} 做名字匹配用的那个名字,两边口径一致。
     * 其次读 spring-messaging 通道的 bean 名。都拿不到才退化到类名。
     *
     * <p><b>刻意不用 {@code toString()} 兜底</b>:未命名的通道会落到
     * {@code Object.toString()} 输出里带 identity hash,进了 span 名就是无界基数 ——
     * 而 span 名对 localRoot span 正是 {@code ErrorLimiter} 的记账 key,基数爆了等于记账失效。
     * 类名虽然信息量少,但稳定且有界。
     *
     * <p>用 {@code instanceof} 而不是 {@code AopUtils.getTargetClass()}:这里判的是接口,
     * 无论 JDK 动态代理还是 CGLIB 子类代理都能命中,比解代理更稳。
     */
    static String channelName(MessageChannel channel) {
        if (channel == null) {
            return UNKNOWN;
        }
        try {
            if (channel instanceof NamedComponent named) {
                String name = named.getComponentName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
            if (channel instanceof AbstractMessageChannel springMessagingChannel) {
                String name = springMessagingChannel.getBeanName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
            String className = channel.getClass().getSimpleName();
            // 匿名类的 getSimpleName() 是空串
            return className.isBlank() ? UNKNOWN : className;
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    /** 按标准优先级解析 destination。 */
    String destination(Message<?> message, MessageChannel channel) {
        String fromHeader = brokerDestinationFromHeaders(message);
        if (fromHeader != null) {
            return fromHeader;
        }
        return destination(channelName(channel));
    }

    /** 去掉方向后缀得到 destination;带上限缓存。会先问 binding 配置。 */
    String destination(String channelName) {
        if (channelName == null || channelName.isBlank()) {
            return UNKNOWN;
        }
        String cached = cache.get(channelName);
        if (cached != null) {
            return cached;
        }
        String destination = resolveFromBindingOrChannel(channelName);
        if (cache.size() < MAX_CACHE_SIZE) {
            cache.putIfAbsent(channelName, destination);
        }
        return destination;
    }

    private String resolveFromBindingOrChannel(String channelName) {
        if (bindingDestinationLookup != null) {
            try {
                String configured = bindingDestinationLookup.apply(channelName);
                if (configured != null && !configured.isBlank()) {
                    return looksLikeBindingName(configured) ? stripBindingSuffix(configured) : configured;
                }
            } catch (RuntimeException ignored) {
                // binding 查询失败不影响发消息,退回 channel 名
            }
        }
        return stripBindingSuffix(channelName);
    }

    static String brokerDestinationFromHeaders(Message<?> message) {
        if (message == null) {
            return null;
        }
        MessageHeaders headers = message.getHeaders();
        if (headers == null) {
            return null;
        }
        for (String key : BROKER_DESTINATION_HEADERS) {
            Object value = headers.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /** yaml 未配 destination 时 SCS 默认填 binding 全名,仍带 {@code -in-0}。 */
    private static boolean looksLikeBindingName(String name) {
        return BINDING_SUFFIX.matcher(name).find();
    }

    /**
     * {@code order.payed-out-0} -> {@code order.payed}。
     * 无方向后缀(如 {@code StreamBridge} 直接裸 destination)时原样返回。
     * 剥离后为空(channel 名就叫 {@code -out-0})时也原样返回,不产出空 span 名。
     */
    private static String stripBindingSuffix(String channelName) {
        String stripped = BINDING_SUFFIX.matcher(channelName).replaceFirst("");
        return stripped.isEmpty() ? channelName : stripped;
    }
}
