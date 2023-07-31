package com.jiduauto.monitor.log;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Set;

/**
 * @author yp
 * @date 2023/07/12
 */
@Component
@ConfigurationProperties("monitor.log")
@ConditionalOnProperty(prefix = "monitor.log", name = "enable", matchIfMissing = true)
@Getter
@Setter
class MonitorLogProperties {
    /**
     * 服务名，默认取值：${spring.application.name}
     */
    private String appName;
    /**
     * 开启核心监控，提供统一日志参数收集与aop参数收集.
     */
    private boolean enable = true;
    /**
     * 解析feign调用结果的默认表达式，默认校验返回编码是否等于0或者200有一个匹配即认为调用成功,多个表达式直接逗号分割.
     * 注意，如果表达式前以"+"开头，则表示在原有默认表达式的基础上追加，否则会覆盖原默认表达式
     */
    private String globalDefaultBoolExpr = "+$.code==0,$.code==200";
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
    /**
     * redis监控配置
     */
    private RedisProperties redis = new RedisProperties();
    /**
     * httpClient监控配置
     */
    private HttpClientProperties httpclient = new HttpClientProperties();

    @PostConstruct
    private void init() {
        if (StringUtils.isBlank(this.appName)) {
            this.appName = SpringUtils.getApplicationName();
        }
        boolean isDevOrTest = SpringUtils.isTargetEnv("dev", "test", "local");
        if (printer.printDetailLog == null) {
            printer.printDetailLog = isDevOrTest;
        }
        if (web.printWebServerDetailLog == null) {
            web.printWebServerDetailLog = isDevOrTest;
        }
        if (grpc.printGrpcClientDetailLog == null) {
            grpc.printGrpcClientDetailLog = isDevOrTest;
        }
        if (grpc.printGrpcServerDetailLog == null) {
            grpc.printGrpcServerDetailLog = isDevOrTest;
        }
        if (xxljob.printXxljobDetailLog == null) {
            xxljob.printXxljobDetailLog = isDevOrTest;
        }
        if (feign.printFeignClientDetailLog == null) {
            feign.printFeignClientDetailLog = isDevOrTest;
        }
        if (feign.printFeignServerDetailLog == null) {
            feign.printFeignServerDetailLog = isDevOrTest;
        }
        if (mybatis.printMybatisDetailLog == null) {
            mybatis.printMybatisDetailLog = isDevOrTest;
        }
        if (rocketmq.printRocketmqConsumerDetailLog == null) {
            rocketmq.printRocketmqConsumerDetailLog = isDevOrTest;
        }
        if (rocketmq.printRocketmqProducerDetailLog == null) {
            rocketmq.printRocketmqProducerDetailLog = isDevOrTest;
        }
        if (redis.printRedisDetailLog == null) {
            redis.printRedisDetailLog = isDevOrTest;
        }
        if (httpclient.printHttpclientDetailLog == null) {
            httpclient.printHttpclientDetailLog = isDevOrTest;
        }
    }

    @Getter
    @Setter
    static class PrinterProperties {
        /**
         * 是否输出各个流量出入口的详情日志(总开关)，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printDetailLog;
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
    static class WebProperties {
        /**
         * 开启web监控
         */
        private boolean enable = true;
        /**
         * 是否输出各个http(非rpc类)入口流量的详情日志, dev/test环境下默认true，其它环境默认false
         */
        private Boolean printWebServerDetailLog;
        /**
         * 不监控的url清单，支持模糊路径如a/*， 默认值：/actuator/health, /misc/ping, /actuator/prometheus
         */
        private Set<String> urlBlackList = Sets.newHashSet("/actuator/health", "/misc/ping", "/actuator/prometheus");
    }

    @Getter
    @Setter
    static class GrpcProperties {
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
        /**
         * 是否输出各个grpc入口流量的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printGrpcServerDetailLog;
        /**
         * 是否输出各个grpc出口流量的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printGrpcClientDetailLog;
    }

    @Getter
    @Setter
    static class XxljobProperties {
        /**
         * 开启xxljob监控
         */
        private boolean enable = true;
        /**
         * 是否输出各个xxljob流量的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printXxljobDetailLog;
    }

    @Getter
    @Setter
    static class FeignProperties {
        /**
         * 开启feign监控
         */
        private boolean enable = true;
        /**
         * 是否输出各个feign入口流量的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printFeignServerDetailLog;
        /**
         * 是否输出各个feign出口流量的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printFeignClientDetailLog;
        /**
         * 解析feign调用结果的默认表达式，默认校验返回编码是否等于0或者200有一个匹配即认为调用成功,多个表达式直接逗号分割.
         * 注意，如果表达式前以"+"开头，则表示在原有默认表达式的基础上追加，否则会覆盖原默认表达式
         */
        private String defaultBoolExpr = "+$.status==200";

        /**
         * 不监控的url清单，支持模糊路径如a/*， 默认值：/actuator/health, /misc/ping, /actuator/prometheus
         */
        private Set<String> urlBlackList = Sets.newHashSet("/actuator/health", "/misc/ping", "/actuator/prometheus");
    }

    @Getter
    @Setter
    static class MybatisProperties {
        /**
         * 开启mybatis监控
         */
        private boolean enable = true;
        /**
         * 是否输mybatis的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printMybatisDetailLog;
        /**
         * mybatis慢sql阈值，单位毫秒.
         */
        private long longQueryTime = 2000;
    }

    @Getter
    @Setter
    static class RocketMqProperties {
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
        /**
         * 是否输出各个rocketmq消费者流量的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printRocketmqConsumerDetailLog;
        /**
         * 是否输出各个rocketmq发送者流量的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printRocketmqProducerDetailLog;
    }

    @Getter
    @Setter
    static class RedisProperties {
        /**
         * 开启redis监控
         */
        private boolean enable = true;
        /**
         * 是否输redis的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printRedisDetailLog;
    }

    @Getter
    @Setter
    static class HttpClientProperties {
        /**
         * 开启httpClient监控
         */
        private boolean enable = true;
        /**
         * 是否输出httpClient的详情日志，dev/test环境下默认true，其它环境默认false
         */
        private Boolean printHttpclientDetailLog;

        /**
         * 不监控的url清单，支持模糊路径如a/*
         */
        private Set<String> urlBlackList;

        /**
         * 仅监控的url清单，支持模糊路径如a/*,仅当此配置不空且元素个数大于0时才生效
         */
        private Set<String> urlWhiteList;
    }
}
