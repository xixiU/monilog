package com.jiduauto.log;

import com.jiduauto.log.enums.LogPoint;
import com.jiduauto.log.enums.MonitorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yp
 * @date 2023/07/12
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MonitorLog {
    LogPoint value();

    /**
     * 指标tag
     *
     */
    String[] tags() default { "" };
}
