package com.minitelemetry.sdk.instrumentation.mq;

import org.springframework.messaging.MessagingException;

/** MQ 异常到 span 状态的映射。 */
final class MessagingErrors {

    /** 拆包步数上限,防御 cause 链自引用。 */
    private static final int MAX_UNWRAP_DEPTH = 10;

    /**
     * 剥掉 Spring 的消息传递异常外壳,取真正的业务异常。两个理由都不是洁癖:
     * <ul>
     *   <li><b>错误分组</b>:通道把 handler 抛的任何异常都包成 {@code MessageDeliveryException},
     *       不拆包则所有 MQ 错误的 {@code exception.type} 都是同一个框架类型,看板上无法区分</li>
     *   <li><b>数据安全</b>:{@code MessageDeliveryException.getMessage()} 内容
     *       含 {@code failedMessage.toString()},即整条消息的 payload。直接记进
     *       {@code exception.message} 等于把业务数据(可能含个人信息)写进 span 属性并上报</li>
     * </ul>
     *
     * <p>只剥 {@link MessagingException} 这一层语义明确的框架异常,不做通用 root cause 追溯 ——
     * 业务异常自己的 cause 链是有意义的,不该被吃掉。
     *
     * @param error 原始异常,可为 {@code null}
     * @return 剥壳后的异常;入参 {@code null} 时返回 {@code null}
     */
    static Throwable unwrap(Throwable error) {
        Throwable current = error;
        for (int i = 0; i < MAX_UNWRAP_DEPTH; i++) {
            if (!(current instanceof MessagingException)) {
                return current;
            }
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                return current;
            }
            current = cause;
        }
        return current;
    }

    private MessagingErrors() {
    }
}
