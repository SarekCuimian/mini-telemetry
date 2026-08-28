package com.minitelemetry.sdk.propagation;

/**
 * 向载体(carrier)写入传播字段的适配器,inject 侧使用。
 *
 * <p>通常以方法引用提供,如 {@code RequestTemplate::header}。
 * 实现必须容忍 {@code carrier == null}(直接 no-op),不得抛异常。
 *
 * @param <C> 载体类型
 */
@FunctionalInterface
public interface TextMapSetter<C> {

    /** 写入 key/value;carrier 为 null 时应静默忽略。同名 key 建议覆盖而非追加。 */
    void set(C carrier, String key, String value);
}
