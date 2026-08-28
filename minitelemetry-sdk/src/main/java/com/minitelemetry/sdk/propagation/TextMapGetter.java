package com.minitelemetry.sdk.propagation;

/**
 * 从载体(carrier)读取传播字段的适配器,extract 侧使用。
 *
 * <p>通常以方法引用提供,如 {@code HttpServletRequest::getHeader}。
 * key 的大小写语义由 carrier 自身决定(HTTP header 不区分大小写,MQ property 区分)。
 * 实现必须容忍 {@code carrier == null},返回 {@code null} 即可,不得抛异常。
 *
 * @param <C> 载体类型
 */
@FunctionalInterface
public interface TextMapGetter<C> {

    /** 读取指定 key 的值;不存在或 carrier 为 null 时返回 {@code null}。 */
    String get(C carrier, String key);
}
