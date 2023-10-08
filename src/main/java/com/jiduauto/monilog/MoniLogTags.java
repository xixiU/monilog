package com.jiduauto.monilog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * tag标记
 * 配合@MoniLog注解使用，在web接口此注解单独使用即可生效。
 * 详见：https://wiki.jiduauto.com/pages/viewpage.action?pageId=674041052
 * @author rongjie.yuan
 * @date 2023/7/25 11:37
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MoniLogTags {
    /**
     * 注意，这里指定的tag值应当是稳定可枚举的，不能使用不可枚举的值，例如用户ID、订单号、时间戳等。如果使用这种不可枚举的值会导致promethues输出过多的监控指标，会给业务应用带来压力
     */
    String[] value();
}
