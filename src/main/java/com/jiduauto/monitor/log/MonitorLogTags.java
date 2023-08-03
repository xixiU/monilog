package com.jiduauto.monitor.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @description: tag标记
 * @author rongjie.yuan
 * @date 2023/7/25 11:37
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MonitorLogTags {
    String[] tags();
}
