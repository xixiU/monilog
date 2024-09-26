package com.jiduauto.monilog;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author yp
 * @date 2023/07/12
 */
@ConfigurationProperties("monilog")
@Getter
@Setter
@Slf4j
class MoniLogProperties implements InitializingBean {
    /**
     * 服务名，默认取值：${spring.application.name}
     */
    private String appName;
    /**
     * 整个组件的总开关：开启后，会进行prometheus打点监控和统一日志参数收集与打印.
     */
    private boolean enable = true;
    /**
     * 开启监控埋点的总开关，若关闭后，将只会打印日志不做prometheus打点。默认开启(日志+监控)
     */
    private boolean enableMonitor = true;
    /**
     * 调试开关,开启时，可更详细的观测到框架执行的异常和日志详情
     */
    private boolean debug = false;
    /**
     * Monilog日志前缀(不支持运行时修改), 默认值: monilog_
     */
    private String logPrefix = "monilog_";
    /**
     * 记录耗时长的操作
     */
    private boolean monitorLongRt = true;
    /**
     * 是否开启 LOGO
     */
    private boolean banner = true;

    /**
     * 解析调用结果的默认表达式，默认校验返回编码是否等于0或者200有一个匹配即认为调用成功,多个表达式直接逗号分割.
     * 注意，如果表达式前以"+"开头，则表示在原有默认表达式的基础上追加，否则会覆盖原默认表达式
     */
    private String globalDefaultBoolExpr = "+$.code==0,$.code==200,$.Code==0,$.Code==200";
    /**
     * 监控开启组件清单，默认为支持的全部组件，当前支持的组件名参考: ComponentEnum类，可以一键设置开启.
     */
    private Set<ComponentEnum> componentIncludes = new HashSet<>(Arrays.asList(ComponentEnum.values()));
    /**
     * 监控不开启组件清单，默认为为空，当前支持的组件名参考: ComponentEnum类，可以一键设置开启，可以一键排除设置开启.
     */
    private Set<ComponentEnum> componentExcludes;
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
     * kafka监控配置
     */
    private KafkaProperties kafka = new KafkaProperties();
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
        String app = System.getProperty("monilog.app-name");
        if (SpringUtils.IS_READY) {
            System.setProperty("monilog.app-name", (this.appName = SpringUtils.application));
        } else if (StringUtils.isNotBlank(app)) {
            return (this.appName = app);
        }
        return this.appName;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        bindValue();
        getAppName();
        // banner输出
        String isInitialized = System.getProperty("monilog.isBannerPrinted", "N");
        if ("N".equals(isInitialized)) {
            printBanner();
            System.setProperty("monilog.isBannerPrinted", "Y");
        }
        MoniLogUtil.addSystemRecord();
        // 启用配置更新
        ApolloListenerRegistry.register(this::bindValue);
    }

    private void bindValue() {
        ApplicationContext applicationContext = SpringUtils.getApplicationContext();
        if (applicationContext == null) {
            log.warn(MoniLogUtil.INNER_DEBUG_LOG_PREFIX + "properties bind failed,applicationCtx is null");
            return;
        }
        try {
            BindResult<MoniLogProperties> monilogBindResult = Binder.get(applicationContext.getEnvironment()).bind("monilog", MoniLogProperties.class);
            if (!monilogBindResult.isBound()) {
                return;
            }
            log.info("monilog properties binding...");
            // 当存在属性值进行属性替换，防止配置不生效
            MoniLogProperties newProp = monilogBindResult.get();
            Field[] fields = MoniLogProperties.class.getDeclaredFields();
            for (Field field : fields) {
                try {
                    if (Modifier.isFinal(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    field.set(this, field.get(newProp));
                } catch (IllegalAccessException e) {
                    // 处理异常
                    log.warn(MoniLogUtil.INNER_DEBUG_LOG_PREFIX + "properties bind error, illegalAccess");
                }
            }
        } finally {
            resetDefaultBoolExpr(this.getFeign(), this.getHttpclient(), this.getGlobalDefaultBoolExpr());
        }
    }


    private static void resetDefaultBoolExpr(FeignProperties feign, HttpClientProperties httpclient, String globalDefaultBoolExpr) {
        if (feign != null) {
            feign.resetDefaultBoolExpr(globalDefaultBoolExpr);
        }
        if (httpclient != null) {
            httpclient.resetDefaultBoolExpr(globalDefaultBoolExpr);
        }
    }

    private void printBanner() {
        if (!banner) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("spring.profiles.active", SpringUtils.activeProfile);
        placeholders.put("spring.application.name", getAppName());
        placeholders.put("monilog.version", MoniLogAutoConfiguration.class.getPackage().getImplementationVersion());
        new MoniLogBanner(placeholders).printBanner();
    }


    @Getter
    @Setter
    static class PrinterProperties {
        /**
         * 流量出入口的的摘要日志输出级别总开关，默认总是输出
         */
        private LogOutputLevel digestLogLevel = LogOutputLevel.always;
        /**
         * 流量出入口的的详情日志输出级别总开关，默认仅失败时输出
         */
        private LogOutputLevel detailLogLevel = LogOutputLevel.onFail;
        /**
         * 慢操作日志输出开关，默认prometheus与日志均打印
         */
        private LogLongRtLevel longRtLevel = LogLongRtLevel.both;
        /**
         * 默认详情日志打印最长的长度，目前仅限制了收集参数中的input与output的长度
         */
        private Integer maxTextLen = 10000;
        /**
         * 日志打印时要排除的组件名称列表，默认为空，即所有类型的都会打印。支持的组件名称参考ComponentEnum
         */
        private Set<String> excludeComponents;
        /**
         * 日志打印时要排除的服务(简单类名)列表，默认为空，即所有方法的都会打印,支持模糊匹配
         */
        private Set<String> excludeServices;
        /**
         * 日志打印时要排除的方法名清单，默认为空，即所有服务的都会打印,支持模糊匹配
         */
        private Set<String> excludeActions;
        /**
         * 日志打印时要排除的异常类(简单类名)清单，通过错误的canonicalName类名判断，使用contains判断。默认为空，即所有错误的都会打印
         */
        private Set<String> excludeExceptions;
        /**
         * 日志打印时要排除的错误关键词清单,使用contains判断。默认为空，即所有错误的都会打印
         */
        private Set<String> excludeKeyWords;
        /**
         * 日志打印时要排除的错误码清单，默认为空
         */
        private Set<String> excludeMsgCodes;
        /**
         * 日志信息中是否再次输出traceId，默认不输出；
         * 在告警时会在信息主体部分添加traceId，方便排查问题。但是若开启的话，在日志平台直接观察时traceId会重复打印
         */
        private boolean printTraceId = false;
        /**
         * 日志输出级别配置
         */
        private LogLevelConfig logLevel = new LogLevelConfig();
    }

    @Getter
    @Setter
    static class LogLevelConfig {
        /**
         * 当接口响应结果被判定为false时，monilog输出的日志级别
         */
        private LogLevel falseResult = LogLevel.ERROR;
        /**
         * 当出现慢调用时，monilog输出的日志级别
         */
        private LogLevel longRt = LogLevel.ERROR;
        /**
         * 当出现超大值时，monilog输出的日志级别
         */
        private LogLevel largeSize = LogLevel.ERROR;
    }

    @Getter
    @Setter
    static class WebProperties {
        /**
         * 开启web监控+日志
         */
        private boolean enable = true;
        /**
         * http(非rpc类)入口流量的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel detailLogLevel;
        /**
         * 不监控的url清单，支持模糊路径如a/*， 默认值：/actuator/**, /misc/ping
         */
        private Set<String> urlBlackList = Sets.newHashSet("/actuator/**", "/misc/ping");

        /**
         * web慢接口，单位毫秒.
         */
        private long longRt = 3000;

    }

    @Getter
    @Setter
    static class GrpcProperties {
        /**
         * 开启grpc监控+日志
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
         * grpc入口流量的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel serverDetailLogLevel;
        /**
         * grpc出口流量的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel clientDetailLogLevel;

        /**
         * grpc慢接口，单位毫秒.
         */
        private long longRt = 3000;
    }

    @Getter
    @Setter
    static class XxljobProperties {
        /**
         * 开启xxljob监控+日志
         */
        private boolean enable = true;
        /**
         * xxljob的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel detailLogLevel;

        /**
         * xxljob慢接口，单位毫秒.
         */
        private long longRt = -1;
    }

    @Getter
    @Setter
    static class FeignProperties {
        /**
         * 开启feign监控+日志
         */
        private boolean enable = true;
        /**
         * feign入口流量的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel serverDetailLogLevel;
        /**
         * feign出口流量的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel clientDetailLogLevel;
        /**
         * 解析feign调用结果的默认表达式，默认校验返回编码是否等于0或者200有一个匹配即认为调用成功,多个表达式直接逗号分割.
         * 注意，如果表达式前以"+"开头，则表示在原有默认表达式的基础上追加，否则会覆盖原默认表达式
         */
        private String defaultBoolExpr = "+$.status==200";

        /**
         * 不监控的url清单，支持模糊路径如a/*， 默认值：/actuator/**, /misc/ping
         */
        private Set<String> urlBlackList = Sets.newHashSet("/actuator/**", "/misc/ping");

        /**
         * feign慢接口，单位毫秒.
         */
        private long longRt = 3000;

        void resetDefaultBoolExpr(String globalDefaultBoolExpr) {
            this.defaultBoolExpr = ResultParser.mergeBoolExpr(globalDefaultBoolExpr, defaultBoolExpr);
        }
    }

    @Getter
    @Setter
    static class MybatisProperties {
        /**
         * 开启mybatis监控+日志
         */
        private boolean enable = true;
        /**
         * mybatis的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel detailLogLevel;
        /**
         * mybatis慢sql阈值，单位毫秒.
         */
        private long longRt = 3000;
    }

    @Getter
    @Setter
    static class RocketMqProperties {
        /**
         * 开启rocketmq监控+日志
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
         * rocketmq消费者的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel consumerDetailLogLevel;
        /**
         * rocketmq发送者的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel producerDetailLogLevel;
        /**
         * rocketmq慢接口，单位毫秒.
         */
        private long longRt = -1;
    }

    @Getter
    @Setter
    static class KafkaProperties {
        /**
         * 开启kafka监控+日志
         */
        private boolean enable = true;
        /**
         * 开启kafka消费者监控
         */
        private boolean consumerEnable = true;
        /**
         * 开启kafka生产者监控
         */
        private boolean producerEnable = true;
        /**
         * kafka消费者的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel consumerDetailLogLevel;
        /**
         * kafka发送者的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel producerDetailLogLevel;
        /**
         * kafka慢接口，单位毫秒.
         */
        private long longRt = -1;
    }

    @Getter
    @Setter
    static class RedisProperties {
        /**
         * 开启redis监控+日志
         */
        private boolean enable = true;
        /**
         * redis的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel detailLogLevel;
        /**
         * redis大值监控日志输出阀值，单位: KB， 默认:10KB， 即超过5KB的缓存，将打印error日志(注意，仅对redis的读取类接口的结果大小做监控)
         */
        private float warnForValueLength = 50;

        /**
         * redis慢rt阈值，单位毫秒. 默认200ms
         */
        private long longRt = 200;
    }

    @Getter
    @Setter
    static class HttpClientProperties {
        /**
         * 开启httpClient监控+日志
         */
        private boolean enable = true;
        /**
         * httpClient的详情日志输出级别，默认仅失败时输出
         */
        private LogOutputLevel detailLogLevel;

        /**
         * 不监控的url清单，支持模糊路径如a/*
         */
        private Set<String> urlBlackList = Sets.newHashSet("/v1/status/leader","/v1/health/service/**");

        /**
         * 不监控的host清单，支持模糊路径如a/*,仅当此配置不空且元素个数大于0时才生效
         */
        private Set<String> hostBlackList;

        /**
         * 不监控的特定业务client类，支持模糊路径如com.ecwid.**，注意模糊匹配语法：?匹配单个字符，*匹配0个或多个字符，**匹配0个或多个目录
         */
        private Set<String> clientBlackList = Sets.newHashSet("com.ecwid.consul.**","com.orbitz.consul.**");

        /**
         * 解析httpClient调用结果的默认表达式，默认校验返回编码是否等于0或者200有一个匹配即认为调用成功,多个表达式直接逗号分割.
         * 注意，如果表达式前以"+"开头，则表示在原有默认表达式的基础上追加，否则会覆盖原默认表达式
         */
        private String defaultBoolExpr = "+$.status==200";

        /**
         * httpClient慢接口，单位毫秒.
         */
        private long longRt = 3000;

        void resetDefaultBoolExpr(String globalDefaultBoolExpr) {
            this.defaultBoolExpr = ResultParser.mergeBoolExpr(globalDefaultBoolExpr, defaultBoolExpr);
        }
    }
}
