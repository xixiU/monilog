package com.jiduauto.monilog;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * @author yp
 * @date 2023/07/12
 */
@ConfigurationProperties("monilog")
@ConditionalOnProperty(prefix = "monilog", name = "enable", matchIfMissing = true)
@Getter
@Setter
class MoniLogProperties implements InitializingBean {
    /**
     * 服务名，默认取值：${spring.application.name}
     */
    private String appName;
    /**
     * 开启核心监控，提供统一日志参数收集与aop参数收集.
     */
    private boolean enable = true;

    /**
     * 调试模式
     */
    private boolean debug = true;
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

    public String getAppName() {
        if (StringUtils.isNotBlank(this.appName)) {
            return this.appName;
        }
        System.setProperty("monilog.appName", (this.appName = SpringUtils.getApplicationName()));
        return this.appName;
    }

    boolean isComponentEnable(String componentName, Boolean componentEnable) {
        if (!Boolean.TRUE.equals(componentEnable)) {
            return false;
        }
        Set<String> componentIncludes = getComponentIncludes();
        if (CollectionUtils.isEmpty(componentIncludes)) {
            return false;
        }
        if (componentIncludes.contains("*") || componentIncludes.contains(componentName)) {
            Set<String> componentExcludes = getComponentExcludes();
            if (CollectionUtils.isEmpty(componentExcludes)) {
                return true;
            }
            return !componentExcludes.contains("*") && !componentExcludes.contains(componentName);
        }
        return false;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (printer.detailLogLevel == null) {
            printer.detailLogLevel = LogOutputLevel.onException;
        }
        LogOutputLevel defaultLevel = printer.detailLogLevel;
        if (web.detailLogLevel == null) {
            web.detailLogLevel = defaultLevel;
        }
        if (grpc.clientDetailLogLevel == null) {
            grpc.clientDetailLogLevel = defaultLevel;
        }
        if (grpc.serverDetailLogLevel == null) {
            grpc.serverDetailLogLevel = defaultLevel;
        }
        if (xxljob.detailLogLevel == null) {
            xxljob.detailLogLevel = defaultLevel;
        }
        if (feign.clientDetailLogLevel == null) {
            feign.clientDetailLogLevel = defaultLevel;
        }
        if (feign.serverDetailLogLevel == null) {
            feign.serverDetailLogLevel = defaultLevel;
        }
        if (mybatis.detailLogLevel == null) {
            mybatis.detailLogLevel = defaultLevel;
        }
        if (rocketmq.consumerDetailLogLevel == null) {
            rocketmq.consumerDetailLogLevel = defaultLevel;
        }
        if (rocketmq.producerDetailLogLevel == null) {
            rocketmq.producerDetailLogLevel = defaultLevel;
        }
        if (redis.detailLogLevel == null) {
            redis.detailLogLevel = defaultLevel;
        }
        if (httpclient.detailLogLevel == null) {
            httpclient.detailLogLevel = defaultLevel;
        }
        feign.resetDefaultBoolExpr(globalDefaultBoolExpr);
    }

    @Getter
    @Setter
    static class PrinterProperties {
        /**
         * 流量出入口的的详情日志输出级别总开关，默认仅异常时输出
         */
        private LogOutputLevel detailLogLevel = LogOutputLevel.onException;
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
         * http(非rpc类)入口流量的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel detailLogLevel = LogOutputLevel.onException;
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
         * grpc入口流量的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel serverDetailLogLevel = LogOutputLevel.onException;
        /**
         * grpc出口流量的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel clientDetailLogLevel = LogOutputLevel.onException;
    }

    @Getter
    @Setter
    static class XxljobProperties {
        /**
         * 开启xxljob监控
         */
        private boolean enable = true;
        /**
         * xxljob的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel detailLogLevel = LogOutputLevel.onException;
    }

    @Getter
    @Setter
    static class FeignProperties {
        /**
         * 开启feign监控
         */
        private boolean enable = true;
        /**
         * feign入口流量的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel serverDetailLogLevel = LogOutputLevel.onException;
        /**
         * feign出口流量的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel clientDetailLogLevel = LogOutputLevel.onException;
        /**
         * 解析feign调用结果的默认表达式，默认校验返回编码是否等于0或者200有一个匹配即认为调用成功,多个表达式直接逗号分割.
         * 注意，如果表达式前以"+"开头，则表示在原有默认表达式的基础上追加，否则会覆盖原默认表达式
         */
        private String defaultBoolExpr = "+$.status==200";

        /**
         * 不监控的url清单，支持模糊路径如a/*， 默认值：/actuator/health, /misc/ping, /actuator/prometheus
         */
        private Set<String> urlBlackList = Sets.newHashSet("/actuator/health", "/misc/ping", "/actuator/prometheus");

        public void resetDefaultBoolExpr(String globalDefaultBoolExpr) {
            this.defaultBoolExpr = ResultParser.mergeBoolExpr(globalDefaultBoolExpr, defaultBoolExpr);
        }
    }

    @Getter
    @Setter
    static class MybatisProperties {
        /**
         * 开启mybatis监控
         */
        private boolean enable = true;
        /**
         * mybatis的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel detailLogLevel = LogOutputLevel.onException;
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
         * rocketmq消费者的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel consumerDetailLogLevel = LogOutputLevel.onException;
        /**
         * rocketmq发送者的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel producerDetailLogLevel = LogOutputLevel.onException;
    }

    @Getter
    @Setter
    static class RedisProperties {
        /**
         * 开启redis监控
         */
        private boolean enable = true;
        /**
         * redis的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel detailLogLevel = LogOutputLevel.onException;
        /**
         * redis大值监控日志输出阀值，单位: KB， 默认:5KB， 即超过5KB的缓存，将打印error日志
         */
        private float warnForValueLength = 5;
    }

    @Getter
    @Setter
    static class HttpClientProperties {
        /**
         * 开启httpClient监控
         */
        private boolean enable = true;
        /**
         * httpClient的详情日志输出级别，默认仅异常时输出
         */
        private LogOutputLevel detailLogLevel = LogOutputLevel.onException;

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
