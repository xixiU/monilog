package com.jiduauto.log.core.annotation;

import com.jiduauto.log.core.enums.LogPoint;

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

}
