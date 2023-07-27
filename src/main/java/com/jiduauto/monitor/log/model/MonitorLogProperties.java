package com.jiduauto.monitor.log.model;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
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
@Setter
public class MonitorLogProperties {
    /**
     * 开启核心监控，提供统一日志参数收集与aop参数收集.
     */
    private boolean enable = true;
    /**
     * 监控开启组件清单，默认为*，目前支持feign,grpc,mybatis,rocketmq,web,xxljob，可以一键设置开启.
     */
    private Set<String> componentIncludes = Sets.newHashSet("*");
    /**
     * 监控不开启组件清单，默认为为空，目前支持feign,grpc,mybatis,rocketmq,web,xxljob，可以一键设置不开启.
     */
    private Set<String> componentExcludes;
    /**
     * 日志打印配置
     */
    private PrinterProperties printer = new PrinterProperties();
    /**
     * web监控配置
     */
    private WebProperties web = new WebProperties();
    /**
     * grpc监控配置
     */
    private GrpcProperties grpc = new GrpcProperties();
    /**
     * xxljob监控配置
     */
    private XxljobProperties xxljob = new XxljobProperties();
    /**
     * feign监控配置
     */
    private FeignProperties feign = new FeignProperties();
    /**
     * mybatis监控配置
     */
    private MybatisProperties mybatis = new MybatisProperties();
    /**
     * rocketmq监控配置
     */
    private RocketMqProperties rocketmq = new RocketMqProperties();

    @Getter
    @Setter
    public static class PrinterProperties {
        /**
         * 默认详情日志打印最长的长度，目前仅限制了收集参数中的input与output的长度
         */
        private Integer maxTextLen = 5000;
        /**
         * 默认info详情日志打印的排除切点类型列表，默认为空，即所有类型的都会打印
         */
        private Set<String> infoExcludeComponents;
        /**
         * 默认info详情日志打印的排除服务列表，默认为空，即所有方法的都会打印,支持模糊匹配
         */
        private Set<String> infoExcludeServices;
        /**
         * 默认info日志打印的排除方法清单，默认为空，即所有服务的都会打印,支持模糊匹配
         */
        private Set<String> infoExcludeActions;
    }

    @Getter
    @Setter
    public static class WebProperties {
        /**
         * 开启web监控
         */
        private boolean enable = true;
        /**
         * 不监控的url清单，支持模糊路径如a/*， 默认值：/actuator/health, /misc/ping, /actuator/prometheus
         */
        private Set<String> urlBlackList = Sets.newHashSet("/actuator/health", "/misc/ping", "/actuator/prometheus");
    }

    @Getter
    @Setter
    public static class GrpcProperties {
        /**
         * 开启grpc监控
         */
        private boolean enable = true;
        /**
         * 开启grpc Server端监控
         */
        private boolean serverEnable = true;
        /**
         * 开启grpc Client端监控
         */
        private boolean clientEnable = true;
    }

    @Getter
    @Setter
    public static class XxljobProperties {
        /**
         * 开启xxljob监控
         */
        private boolean enable = true;
    }

    @Getter
    @Setter
    public static class FeignProperties {
        /**
         * 开启feign监控
         */
        private boolean enable = true;
        /**
         * 解析feign调用结果的默认表达式，默认校验返回编码是否等于0或者200有一个匹配即认为调用成功,多个表达式直接逗号分割.
         */
        private String boolExprDefault = "$.code==200,$.code==0";
    }

    @Getter
    @Setter
    public static class MybatisProperties {
        /**
         * 开启mybatis监控
         */
        private boolean enable = true;
        /**
         * mybatis慢sql阈值，单位毫秒.
         */
        private long longQueryTime = 2000;
    }


    @Getter
    @Setter
    public static class RocketMqProperties {
        /**
         * 开启rocketmq监控
         */
        private boolean enable = true;
        /**
         * 开启rocketmq消费者监控
         */
        private boolean consumerEnable = true;
        /**
         * 开启rocketmq生产者监控
         */
        private boolean producerEnable = true;
    }
}
