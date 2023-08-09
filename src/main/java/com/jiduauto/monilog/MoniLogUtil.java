package com.jiduauto.monilog;

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
    /**
     * 组件监控前缀
     */
    private static final String BUSINESS_MONITOR_PREFIX = "business_monitor_";
    static final String INNER_DEBUG_PREFIX = "__monilog_warn__";

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
        log.warn(INNER_DEBUG_PREFIX + pattern, args);
    }

    private static void doMonitor(MoniLogParams logParams) {
        TagBuilder systemTags = getSystemTags(logParams);
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            logPoint = LogPoint.unknown;
        }
        String[] allTags = systemTags.add(logParams.getTags()).toArray();

        String name = BUSINESS_MONITOR_PREFIX + logPoint.name();
        MetricMonitor.record(name + MonitorType.RECORD.getMark(), allTags);
        // 耗时只打印基础tag
        MetricMonitor.eventDruation(name + MonitorType.TIMER.getMark(), systemTags.toArray()).record(logParams.getCost(), TimeUnit.MILLISECONDS);

        if (logParams.isHasUserTag()) {
            name = name + "_" + logParams.getService() + "_" + logParams.getAction();
            MetricMonitor.eventDruation(name + MonitorType.TIMER.getMark(), allTags).record(logParams.getCost(), TimeUnit.MILLISECONDS);
        }
        if (!checkRtMonitor(logParams)) {
            return;
        }
        // 操作操作信息
        String operationCostTooLongMonitorPrefix = BUSINESS_MONITOR_PREFIX + "rt_too_long_" + logPoint.name();
        MetricMonitor.record(operationCostTooLongMonitorPrefix + MonitorType.RECORD.getMark(), allTags);
        // 耗时只打印基础tag
        MetricMonitor.eventDruation(operationCostTooLongMonitorPrefix + MonitorType.TIMER.getMark(), systemTags.toArray()).record(logParams.getCost(), TimeUnit.MILLISECONDS);
    }


    private static boolean checkRtMonitor(MoniLogParams logParams) {
        MoniLogProperties logProperties = getLogProperties();
        if (logProperties == null || !logProperties.isMonitorLongRt()) {
            return false;
        }
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            return false;
        }
        long cost = logParams.getCost();
        if (cost <= 0) {
            return false;
        }
        switch (logPoint) {
            case xxljob:
                return exceedCostThreshold(logProperties.getXxljob().getLongRt(), cost);
            case redis:
                return exceedCostThreshold(logProperties.getRedis().getLongRt(), cost);
            case mybatis:
                return exceedCostThreshold(logProperties.getMybatis().getLongRt(), cost);
            case grpc_client:
            case grpc_server:
                return exceedCostThreshold(logProperties.getGrpc().getLongRt(), cost);
            case http_client:
                return exceedCostThreshold(logProperties.getHttpclient().getLongRt(), cost);
            case http_server:
                return exceedCostThreshold(logProperties.getWeb().getLongRt(), cost);
            case feign_client:
            case feign_server:
                return exceedCostThreshold(logProperties.getFeign().getLongRt(), cost);
            case rocketmq_consumer:
            case rocketmq_producer:
                return exceedCostThreshold(logProperties.getRocketmq().getLongRt(), cost);
            case unknown:
            case user_define:
            default:
                return false;
        }
    }

    private static boolean exceedCostThreshold(long threshold, long actualCost) {
        if (threshold <= 0) {
            return false;
        }
        return threshold < actualCost;
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
        MoniLogProperties properties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        return (logProperties = properties);
    }
}
