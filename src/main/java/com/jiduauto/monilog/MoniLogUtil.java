package com.jiduauto.monilog;

import ch.qos.logback.classic.spi.EventArgUtil;
import com.carrotsearch.sizeof.RamUsageEstimator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static com.carrotsearch.sizeof.RamUsageEstimator.ONE_KB;

/**
 * 日志工具类
 *
 * @author rongjie.yuan
 * @date 2023/7/17 16:42
 */
@Slf4j
class MoniLogUtil {

    /**
     * 组件监控前缀
     */
    private static final String METRIC_PREFIX = "monilog_";
    // 这个后面的分隔符不能去掉，去掉会导致日志检索的时候查不到对应的日志
    static final String INNER_DEBUG_LOG_PREFIX = "__monilog_warn__|";

    private static MoniLogPrinter logPrinter = null;
    private static MoniLogProperties logProperties = null;

    private static final Timer TIMER = new Timer();

    static {
        addSysRecord();
    }

    /**
     * 添加系统方法指标，每6小时打印一条系统信息
     */
    private static void addSysRecord() {
        // 设置任务的初始延迟时间为3分钟，防止应用启动过程中执行
        long delay = 3 * 60 * 1000;

        // 设置任务的执行间隔时间为6小时
        long period = 6 * 60 * 60 * 1000;
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                MoniLogUtil.addSystemRecord();
            }
        };
        try {
            TIMER.schedule(timerTask, delay, period);
        } catch (Exception e) {
            MoniLogUtil.innerDebug("addSysRecord error", e);
        }
    }

    /**
     * 添加接入组件信息上报，仅上报基础信息，当前接入的版本，接入的应用，环境
     */
    static void addSystemRecord() {
        try {
            TagBuilder tag = TagBuilder.of("application", SpringUtils.application)
                    .add("env", SpringUtils.activeProfile)
                    .add("version", MoniLogAutoConfiguration.class.getPackage().getImplementationVersion());
            MonilogMetrics.record(METRIC_PREFIX + "sysVersion", tag.toArray());
        } catch (Exception e) {
            innerDebug("addSystemRecord error", e);
        }
    }

    static void log(MoniLogParams logParams) {
        try {
            doMonitor(logParams);
        } catch (Exception e) {
            innerDebug("doMonitor error", e);
        }
        try {
            doRtTooLongMonitor(logParams);
        } catch (Exception e) {
            innerDebug("doRtTooLongMonitor error", e);
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
        boolean enableDebugOutput = logProperties != null && logProperties.isDebug();
        if (!enableDebugOutput) {
            Throwable e = EventArgUtil.extractThrowable(args);
            if (e != null && args.length > 0) {
                pattern += " {}";
                args[args.length - 1] = ExceptionUtil.getErrorMsg(e);
            }
        }
        MoniLogPrinter printer = getLogPrinter();
        // 内部异常默认添加traceId
        String traceId = printer == null ? new DefaultMoniLogPrinter().getTraceId() : printer.getTraceId();
        String version = MoniLogAutoConfiguration.class.getPackage().getImplementationVersion();
        String prefix = "[" + traceId + "][" + version + "]" + INNER_DEBUG_LOG_PREFIX;
        log.warn(prefix + pattern, args);
        // 内部异常添加metric上报
        TagBuilder tagBuilder = TagBuilder.of("application", SpringUtils.application);
        MonilogMetrics.record(METRIC_PREFIX + "inner_debug", tagBuilder.toArray());
    }

    private static void doMonitor(MoniLogParams logParams) {
        if (!checkDoMonitor()) {
            return;
        }
        TagBuilder systemTags = getSystemTags(logParams);
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            logPoint = LogPoint.unknown;
        }
        String[] allTags = systemTags.add(logParams.getTags()).toArray();

        String name = METRIC_PREFIX + logPoint.name();
        MonilogMetrics.record(name + MonitorType.RECORD.getMark(), allTags);
        // 耗时只打印基础tag
        MonilogMetrics.eventDuration(name + MonitorType.TIMER.getMark(), systemTags.toArray()).record(logParams.getCost(), TimeUnit.MILLISECONDS);

        if (logParams.isHasUserTag()) {
            name = name + "_" + logParams.getService() + "_" + logParams.getAction();
            MonilogMetrics.eventDuration(name + MonitorType.TIMER.getMark(), allTags).record(logParams.getCost(), TimeUnit.MILLISECONDS);
        }

    }

    private static boolean checkDoMonitor() {
        MoniLogProperties logProperties = getLogProperties();
        return logProperties != null && logProperties.isEnable() && logProperties.isEnableMonitor();
    }

    private static void doRtTooLongMonitor(MoniLogParams logParams) {
        if (!checkRtMonitor(logParams)) {
            return;
        }
        MoniLogProperties logProperties = getLogProperties();
        if (logProperties == null || !logProperties.isMonitorLongRt()) {
            return;
        }
        LogLongRtLevel rtTooLongLevel = logProperties.getPrinter().getLongRtLevel();
        if (rtTooLongLevel == null || LogLongRtLevel.none.equals(rtTooLongLevel)) {
            return;
        }
        TagBuilder systemTags = getSystemTags(logParams);
        LogPoint logPoint = logParams.getLogPoint();
        String[] allTags = systemTags.add(logParams.getTags()).toArray();
        if ((LogLongRtLevel.both.equals(rtTooLongLevel) || LogLongRtLevel.onlyPrometheus.equals(rtTooLongLevel)) && logProperties.isEnableMonitor()) {
            // 操作操作信息
            String operationCostTooLongMonitorPrefix = METRIC_PREFIX + "rt_too_long_" + logPoint.name();
            MonilogMetrics.record(operationCostTooLongMonitorPrefix + MonitorType.RECORD.getMark(), allTags);
            // 耗时只打印基础tag
            MonilogMetrics.eventDuration(operationCostTooLongMonitorPrefix + MonitorType.TIMER.getMark(), systemTags.toArray()).record(logParams.getCost(), TimeUnit.MILLISECONDS);
        }
        if (LogLongRtLevel.both.equals(rtTooLongLevel) || LogLongRtLevel.onlyLogger.equals(rtTooLongLevel)) {
            printLongRtLog(logParams);
        }
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
        String exception = logParams.getException() == null ? "null" : ReflectUtil.getSimpleClassName(logParams.getException().getClass());
        return TagBuilder.of("result", success ? "success" : "error").add("application", SpringUtils.application).add("logPoint", logParams.getLogPoint().name()).add("env", SpringUtils.activeProfile).add("service", logParams.getService()).add("action", logParams.getAction()).add("msgCode", logParams.getMsgCode()).add("exception", exception);
    }

    /**
     * 打印慢操作日志
     */
    static void printLongRtLog(MoniLogParams logParams) {
        MoniLogPrinter printer = getLogPrinter();
        if (printer == null) {
            return;
        }
        printer.logLongRt(logParams);
    }

    /**
     * 打印大值日志
     */
    static void printLargeSizeLog(MoniLogParams p, String key) {
        MoniLogPrinter printer = getLogPrinter();
        if (printer == null) {
            return;
        }
        if (p == null || p.getLogPoint() != LogPoint.redis || p.getOutput() == null || StringUtils.isBlank(key)) {
            return;
        }
        MoniLogProperties moniLogProperties = getLogProperties();
        if (moniLogProperties == null) {
            return;
        }
        MoniLogProperties.RedisProperties redisConf = moniLogProperties.getRedis();
        if (redisConf == null || !redisConf.isEnable() || redisConf.getWarnForValueLength() <= 0) {
            return;
        }
        long valueLen;
        try {
            valueLen = RamUsageEstimator.sizeOf(p.getOutput());
        } catch (Exception e) {
            MoniLogUtil.innerDebug("parseResultSize error", e);
            return;
        }
        if (valueLen > 0 && valueLen > redisConf.getWarnForValueLength() * ONE_KB) {
            printer.logLargeSize(p, key, valueLen);
        }
    }

    private static LogOutputLevel getDigestLogLevel() {
        MoniLogProperties properties = getLogProperties();
        if (properties == null) {
            return LogOutputLevel.always;
        }
        MoniLogProperties.PrinterProperties printerCfg = properties.getPrinter();
        if (printerCfg == null) {
            return LogOutputLevel.always;
        }
        LogOutputLevel detailLogLevel = printerCfg.getDigestLogLevel();
        if (detailLogLevel == null) {
            return LogOutputLevel.always;
        }
        return detailLogLevel;
    }

    /**
     * 打印摘要日志
     */
    private static void printDigestLog(MoniLogParams logParams) {
        MoniLogPrinter printer = getLogPrinter();
        if (printer == null || excludePrint(logParams)) {
            return;
        }
        boolean doPrinter = printLevelCheckPass(getDigestLogLevel(), logParams);
        if (doPrinter) {
            printer.logDigest(logParams);
        }
    }

    /**
     * 打印详情日志
     */
    private static void printDetailLog(MoniLogParams logParams) {
        MoniLogPrinter printer = getLogPrinter();
        MoniLogProperties properties = getLogProperties();
        if (printer == null || properties == null) {
            return;
        }
        if (properties.isDebug()) {
            printer.logDetail(logParams);
            return;
        }
        if (excludePrint(logParams)) {
            return;
        }
        LogOutputLevel detailLogLevel = null;
        LogPoint logPoint = logParams.getLogPoint();

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
                break;
            case unknown:
            default:
                break;
        }
        if (detailLogLevel == null) {
            MoniLogProperties.PrinterProperties printerCfg = properties.getPrinter();
            detailLogLevel = printerCfg.getDetailLogLevel();
            if (detailLogLevel == null) {
                detailLogLevel = LogOutputLevel.onException;
            }
        }
        boolean doPrinter = printLevelCheckPass(detailLogLevel, logParams);

        if (doPrinter) {
            printer.logDetail(logParams);
        }
    }

    private static MoniLogPrinter getLogPrinter() {
        if (logPrinter != null) {
            return logPrinter;
        }
        MoniLogPrinter printer = SpringUtils.getBeanWithoutException(MoniLogPrinter.class);
        return (logPrinter = printer);
    }

    /**
     * 校验是否在排除清单中,若返回true，则不需要打印摘要日志与详情日志
     */
    private static boolean excludePrint(MoniLogParams logParams) {
        MoniLogPrinter printer = getLogPrinter();
        MoniLogProperties properties = getLogProperties();
        if (printer == null || properties == null) {
            return true;
        }
        MoniLogProperties.PrinterProperties printerCfg = properties.getPrinter();
        if (printerCfg == null || logParams == null) {
            return true;
        }
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            return true;
        }

        Set<String> excludeComponents = printerCfg.getExcludeComponents();
        Set<String> excludeServices = printerCfg.getExcludeServices();
        Set<String> excludeActions = printerCfg.getExcludeActions();
        Set<String> excludeMsgCodes = printerCfg.getExcludeMsgCodes();
        Set<String> excludeKeyWords = printerCfg.getExcludeKeyWords();
        Set<String> exceptions = printerCfg.getExcludeExceptions();

        if (StringUtil.checkPathMatch(excludeComponents, logPoint.name())) {
            return true;
        }
        if (StringUtil.checkPathMatch(excludeServices, logParams.getService())) {
            return true;
        }
        if (StringUtil.checkPathMatch(excludeActions, logParams.getAction())) {
            return true;
        }
        // 错误码匹配msgCode
        if (StringUtil.checkContainsIgnoreCase(excludeMsgCodes, logParams.getMsgCode())) {
            return true;
        }
        // 关键词匹配msgInfo
        if (StringUtil.checkListItemContains(excludeKeyWords, logParams.getMsgInfo())) {
            return true;
        }
        // 基于错误的判断
        Throwable exception = logParams.getException();
        if (exception == null) {
            return false;
        }
        // 关键词匹配错误
        if (StringUtil.checkListItemContains(excludeKeyWords, exception.getMessage())) {
            return true;
        }
        // 匹配错误类
        return StringUtil.checkListItemContains(exceptions, exception.getClass().getCanonicalName());
    }

    private static boolean printLevelCheckPass(LogOutputLevel detailLogLevel, MoniLogParams logParams) {
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
        return doPrinter;
    }

    private static MoniLogProperties getLogProperties() {
        if (logProperties != null) {
            return logProperties;
        }
        MoniLogProperties properties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        return (logProperties = properties);
    }
}
