package com.jiduauto.monitor.log;

import com.alibaba.fastjson.JSON;
import com.metric.MetricMonitor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author rongjie.yuan
 * @description: 日志工具类
 * @date 2023/7/17 16:42
 */
@Slf4j
class MonitorLogUtil {
    private static MonitorLogPrinter logPrinter = null;
    private static MonitorLogProperties logProperties = null;

    public static void log(MonitorLogParams logParams) {
        try {
            doMonitor(logParams);
        } catch (Exception e) {
            log("doMonitor error:{}", e.getMessage());
        }
        try {
            printDigestLog(logParams);
        } catch (Exception e) {
            log("printDigestLog error:{}", e.getMessage());
        }
        try {
            printDetailLog(logParams);
        } catch (Exception e) {
            log("printDetailLog error:{}", e.getMessage());
        }
    }

    /**
     * 打印框架日志
     */
    public static void log(String pattern, Object... arg) {
        log.warn("__monitor_log_warn__" + pattern, arg);
    }

    private static void doMonitor(MonitorLogParams logParams) {
        TagBuilder systemTags = getSystemTags(logParams);
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            logPoint = LogPoint.unknown;
        }
        //TODO 这里在后边加入了自定义的tag，可能与全局监控混淆
        String[] allTags = systemTags.add(logParams.getTags()).toArray();

        String name = "business_monitor";
        if (logParams.isHasUserTag()) {
            name = name + "_" + logParams.getService() + "_" + logParams.getAction();
        }
        // TODO rongjie.yuan  2023/7/28 12:45 全局监控与业务监控区分开来。
        name = name + "_" + logPoint.name();
        // 默认打一个record记录
        MetricMonitor.record(name + MonitorType.RECORD.getMark(), allTags);
        try {
            MetricMonitor.eventDruation(name + MonitorType.TIMER.getMark(), allTags).record(logParams.getCost(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            MonitorLogUtil.log("eventDuration name:{}, tag:{}, error:{}", name, JSON.toJSONString(allTags), e.getMessage());
        }
    }


    /**
     * 统一打上环境标、应用名、打标类型、处理结果
     */
    private static TagBuilder getSystemTags(MonitorLogParams logParams) {
        boolean success = logParams.isSuccess() && logParams.getException() == null;
        String exceptionMsg = logParams.getException() == null ? "null" : ExceptionUtil.getErrorMsg(logParams.getException());
        int maxLen = 30;
        if (exceptionMsg.length() > maxLen) {
            exceptionMsg = exceptionMsg.substring(0, maxLen) + "...";
        }
        return TagBuilder.of("result", success ? "success" : "error")
                .add("application", SpringUtils.getApplicationName())
                .add("logPoint", logParams.getLogPoint().name())
                .add("env", SpringUtils.getActiveProfile())
                .add("service", logParams.getService())
                .add("action", logParams.getAction())
                .add("msgCode", logParams.getMsgCode())
                .add("cost", String.valueOf(logParams.getCost()))
                .add("exception", exceptionMsg);
    }

    /**
     * 打印摘要日志
     *
     * @param logParams
     */
    private static void printDigestLog(MonitorLogParams logParams) {
        MonitorLogPrinter printer = getLogPrinter();
        if (printer == null) {
            return;
        }
        printer.logDigest(logParams);
    }

    /**
     * 打印详情日志
     *
     * @param logParams
     */
    private static void printDetailLog(MonitorLogParams logParams) {
        MonitorLogPrinter printer = getLogPrinter();
        MonitorLogProperties properties = getLogProperties();
        if (printer == null || properties == null) {
            return;
        }
        MonitorLogProperties.PrinterProperties printerCfg = properties.getPrinter();
        if (!printerCfg.getPrintDetailLog()) {
            return;
        }
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            return;
        }
        Set<String> infoExcludeComponents = printerCfg.getInfoExcludeComponents();
        Set<String> infoExcludeServices = printerCfg.getInfoExcludeServices();
        Set<String> infoExcludeActions = printerCfg.getInfoExcludeActions();
        if (StringUtil.checkPathMatch(infoExcludeComponents, logPoint.name())) {
            return;
        }
        if (StringUtil.checkPathMatch(infoExcludeServices, logParams.getService())) {
            return;
        }
        if (StringUtil.checkPathMatch(infoExcludeActions, logParams.getAction())) {
            return;
        }
        boolean doPrinter = true;
        switch (logPoint) {
            case http_server:
                doPrinter = properties.getWeb().getPrintWebServerDetailLog();
                break;
            case http_client:
                doPrinter = properties.getHttpclient().getPrintHttpclientDetailLog();
                ;
                break;
            case feign_server:
                doPrinter = properties.getFeign().getPrintFeignServerDetailLog();
                break;
            case feign_client:
                doPrinter = properties.getFeign().getPrintFeignClientDetailLog();
                break;
            case grpc_client:
                doPrinter = properties.getGrpc().getPrintGrpcClientDetailLog();
                break;
            case grpc_server:
                doPrinter = properties.getGrpc().getPrintGrpcServerDetailLog();
                break;
            case rocketmq_consumer:
                doPrinter = properties.getRocketmq().getPrintRocketmqConsumerDetailLog();
                break;
            case rocketmq_producer:
                doPrinter = properties.getRocketmq().getPrintRocketmqProducerDetailLog();
                break;
            case mybatis:
                doPrinter = properties.getMybatis().getPrintMybatisDetailLog();
                break;
            case xxljob:
                doPrinter = properties.getXxljob().getPrintXxljobDetailLog();
                break;
            case redis:
                doPrinter = properties.getRedis().getPrintRedisDetailLog();
            case unknown:
            default:
                break;
        }
        if (doPrinter) {
            printer.logDetail(logParams);
        }
    }

    private static MonitorLogPrinter getLogPrinter() {
        if (logPrinter != null) {
            return logPrinter;
        }
        MonitorLogPrinter printer = null;
        try {
            printer = SpringUtils.getBean(MonitorLogPrinter.class);
        } catch (Exception e) {
            MonitorLogUtil.log(":no MonitorLogPrinter instance found");
        }
        return (logPrinter = printer);
    }

    private static MonitorLogProperties getLogProperties() {
        if (logProperties != null) {
            return logProperties;
        }
        MonitorLogProperties properties = null;
        try {
            properties = SpringUtils.getBean(MonitorLogProperties.class);
        } catch (Exception e) {
            MonitorLogUtil.log(":no MonitorLogProperties instance found");
        }
        return (logProperties = properties);
    }
}
