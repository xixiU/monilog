package com.jiduauto.monilog;

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
class MoniLogUtil {
    private static MoniLogPrinter logPrinter = null;
    private static MoniLogProperties logProperties = null;

    public static void log(MoniLogParams logParams) {
        try {
            doMonitor(logParams);
        } catch (Exception e) {
            innerDebug("doMonitor error", e);
        }
        try {
            printDigestLog(logParams);
        } catch (Exception e) {
            innerDebug("printDigestLog error", e);
        }
        try {
            printDetailLog(logParams);
        } catch (Exception e) {
            innerDebug("printDetailLog error", e);
        }
    }

    /**
     * 打印框架日志
     */
    static void innerDebug(String pattern, Object... args) {
        MoniLogProperties logProperties = getLogProperties();
        if (logProperties != null && !logProperties.isDebug()) {
            return;
        }
        log.warn("__monilog_warn__ " + pattern, args);
    }

    private static void doMonitor(MoniLogParams logParams) {
        TagBuilder systemTags = getSystemTags(logParams);
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            logPoint = LogPoint.unknown;
        }
        //TODO 这里在后边加入了自定义的tag，可能与全局监控混淆
        String[] allTags = systemTags.add(logParams.getTags()).toArray();

        String name = StringUtil.BUSINESS_MONITOR_PREFIX + logPoint.name();
        addMonitor(name, allTags, logParams.getCost());
        if (logParams.isHasUserTag()) {
            name = name + "_" + logParams.getService() + "_" + logParams.getAction();
            addMonitor(name, allTags, logParams.getCost());
        }
    }

    private static void addMonitor(String namePrefix, String[] tags, long cost) {
        try {
            // 打record
            MetricMonitor.record(namePrefix + MonitorType.RECORD.getMark(), tags);
            // 打耗时
            MetricMonitor.eventDruation(namePrefix + MonitorType.TIMER.getMark(), tags).record(cost, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            MoniLogUtil.innerDebug("addMonitor error name:{}, tag:{}", namePrefix, JSON.toJSONString(tags), e);
        }

    }


    /**
     * 统一打上环境标、应用名、打标类型、处理结果
     */
    private static TagBuilder getSystemTags(MoniLogParams logParams) {
        boolean success = logParams.isSuccess() && logParams.getException() == null;
        String exceptionMsg = logParams.getException() == null ? "null" : ExceptionUtil.getErrorMsg(logParams.getException());
        int maxLen = 30;
        if (exceptionMsg.length() > maxLen) {
            exceptionMsg = exceptionMsg.substring(0, maxLen) + "...";
        }
        return TagBuilder.of("result", success ? "success" : "error").add("application", SpringUtils.getApplicationName()).add("logPoint", logParams.getLogPoint().name()).add("env", SpringUtils.getActiveProfile()).add("service", logParams.getService()).add("action", logParams.getAction()).add("msgCode", logParams.getMsgCode()).add("cost", String.valueOf(logParams.getCost())).add("exception", exceptionMsg);
    }

    /**
     * 打印摘要日志
     *
     * @param logParams
     */
    private static void printDigestLog(MoniLogParams logParams) {
        MoniLogPrinter printer = getLogPrinter();
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
    private static void printDetailLog(MoniLogParams logParams) {
        MoniLogPrinter printer = getLogPrinter();
        MoniLogProperties properties = getLogProperties();
        if (printer == null || properties == null) {
            return;
        }
        MoniLogProperties.PrinterProperties printerCfg = properties.getPrinter();
        LogOutputLevel detailLogLevel = printerCfg.getDetailLogLevel();
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

        switch (logPoint) {
            case http_server:
                detailLogLevel = properties.getWeb().getDetailLogLevel();
                break;
            case http_client:
                detailLogLevel = properties.getHttpclient().getDetailLogLevel();

                break;
            case feign_server:
                detailLogLevel = properties.getFeign().getServerDetailLogLevel();
                break;
            case feign_client:
                detailLogLevel = properties.getFeign().getClientDetailLogLevel();
                break;
            case grpc_client:
                detailLogLevel = properties.getGrpc().getClientDetailLogLevel();
                break;
            case grpc_server:
                detailLogLevel = properties.getGrpc().getServerDetailLogLevel();
                break;
            case rocketmq_consumer:
                detailLogLevel = properties.getRocketmq().getConsumerDetailLogLevel();
                break;
            case rocketmq_producer:
                detailLogLevel = properties.getRocketmq().getProducerDetailLogLevel();
                break;
            case mybatis:
                detailLogLevel = properties.getMybatis().getDetailLogLevel();
                break;
            case xxljob:
                detailLogLevel = properties.getXxljob().getDetailLogLevel();
                break;
            case redis:
                detailLogLevel = properties.getRedis().getDetailLogLevel();
            case unknown:
            default:
                break;
        }
        if (detailLogLevel == null) {
            detailLogLevel = LogOutputLevel.onException;
        }
        boolean doPrinter = false;
        switch (detailLogLevel) {
            case always:
                doPrinter = true;
                break;
            case onFail:
                doPrinter = !logParams.isSuccess() || logParams.getException() != null;
                break;
            case onException:
                doPrinter = logParams.getException() != null;
                break;
            case none:
            default:
                break;
        }

        if (doPrinter) {
            printer.logDetail(logParams);
        }
    }

    private static MoniLogPrinter getLogPrinter() {
        if (logPrinter != null) {
            return logPrinter;
        }
        MoniLogPrinter printer = null;
        try {
            printer = SpringUtils.getBean(MoniLogPrinter.class);
        } catch (Exception e) {
            MoniLogUtil.innerDebug(":no MoniLogPrinter instance found", e);
        }
        return (logPrinter = printer);
    }

    private static MoniLogProperties getLogProperties() {
        if (logProperties != null) {
            return logProperties;
        }
        MoniLogProperties properties = null;
        try {
            properties = SpringUtils.getBean(MoniLogProperties.class);
        } catch (Exception e) {
            MoniLogUtil.innerDebug(":no MoniLogProperties instance found", e);
        }
        return (logProperties = properties);
    }
}
