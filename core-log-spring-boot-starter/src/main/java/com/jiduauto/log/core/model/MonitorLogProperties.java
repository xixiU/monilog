package com.jiduauto.log.core.model;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * @author yp
 * @date 2023/07/12
 */
@Configuration
@ConfigurationProperties("monitor.log")
@Getter
public class MonitorLogProperties {
    /**
     *是否开启配置
     */
    private boolean enable;


    /**
     * 日志打印
     */
    private PrinterProperties printer;

    @Getter
    public class PrinterProperties{
        /**
         * 默认详情日志打印最长的长度，目前仅限制了收集参数中的input与output
         */
        private final Integer maxTextLen =5000;

        /**
         * 默认info详情日志打印的排除切点类型列表，默认为空，即所有类型的都会打印
         */
        private Set<String> infoExcludeLogPoint;

        /**
         * 默认info详情日志打印的排除方法列表，默认为空，即所有方法的都会打印,支持模糊匹配
         */
        private Set<String> infoExcludeService;


        /**
         * 默认info日志打印的排除方法清单，默认为空，即所有服务的都会打印,支持模糊匹配
         */
        private Set<String> infoExcludeAction;

    }

}
