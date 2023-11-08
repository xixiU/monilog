package com.jiduauto.monilog;


import com.alibaba.fastjson.JSON;
import com.carrotsearch.sizeof.RamUsageEstimator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * 默认日志打印方式
 */
class DefaultMoniLogPrinter implements MoniLogPrinter {
    @AllArgsConstructor
    @Getter
    enum LogType {
        DETAIL,
        DIGEST,
        LONG_RT,
        LARGE_SIZE;
    }

    private static final String DETAIL_LOG_PATTERN = "{}detail_log[{}]-{}.{}|{}|{}|{}|{}{} 【input】:{}, 【output】:{}";
    private static final String DIGEST_LOG_PATTERN = "{}digest_log[{}]-{}.{}|{}|{}|{}|{}{}";
    private static final String LONG_RT_LOG_PATTERN = "{}rt_too_long[{}]-{}.{}|{}|{}|{}|{}{}";
    private static final String LARGE_SIZE_LOG_PATTERN = "{}size_too_large[{}]-{}.{}[key={}], size: {}, rt:{}";

    private static final String DETAIL_LOG_INCLUDE_TRACE_PATTERN = "[{}]" + DETAIL_LOG_PATTERN;
    private static final String DIGEST_LOG_INCLUDE_TRACE_PATTERN = "[{}]" + DIGEST_LOG_PATTERN;
    private static final String LONG_RT_LOG_INCLUDE_TRACE_PATTERN = "[{}]" + LONG_RT_LOG_PATTERN;
    private static final String LARGE_SIZE_LOG_INCLUDE_TRACE_PATTERN = "[{}]" + LARGE_SIZE_LOG_PATTERN;

    @Resource
    private MoniLogProperties moniLogProperties;

    private String getLogPattern(MoniLogParams p, LogType logType) {
        if (p == null) {
            return "";
        }
        boolean includeTraceId = moniLogProperties.getPrinter().isMessageRepeatTraceId();
        switch (logType) {
            case DETAIL:
                return includeTraceId ? DETAIL_LOG_INCLUDE_TRACE_PATTERN : DETAIL_LOG_PATTERN;
            case DIGEST:
                return includeTraceId ? DIGEST_LOG_INCLUDE_TRACE_PATTERN : DIGEST_LOG_PATTERN;
            case LARGE_SIZE:
                return includeTraceId ? LARGE_SIZE_LOG_INCLUDE_TRACE_PATTERN : LARGE_SIZE_LOG_PATTERN;
            case LONG_RT:
                return includeTraceId ? LONG_RT_LOG_INCLUDE_TRACE_PATTERN : LONG_RT_LOG_PATTERN;
            default:
                // 理论不会
                return "";
        }
    }

    private LogLevel getLogLevel(MoniLogParams p, LogType logType) {
        if (p == null) {
            return null;
        }
        LogLevel level = LogLevel.INFO;
        switch (logType) {
            case DETAIL:
            case DIGEST:
                level = getFalseResultLogLevel();
                level = p.getException() != null ? LogLevel.ERROR : p.isSuccess() || level == LogLevel.INFO ? LogLevel.INFO : level;
                return level;
            case LARGE_SIZE:
                return getLargeSizeLogLevel();
            case LONG_RT:
                return getLongRtLogLevel();
        }
        return level;
    }

    private void logWithLevel(Logger logger, LogLevel level, String pattern, Object... params) {
        switch (level) {
            case INFO:
                logger.info(pattern, params);
                break;
            case WARN:
                logger.warn(pattern, params);
                break;
            case ERROR:
                logger.error(pattern, params);
                break;
        }
    }

    private void logDetailOrDigest(MoniLogParams p, boolean isDetail) {
        if (p == null) {
            return;
        }
        Logger logger = getLogger(p);
        String logPoint = p.getLogPoint().name();
        String service = p.getService();
        String action = p.getAction();
        String success = p.isSuccess() ? "true" : "false";
        String rt = p.getCost() + "ms";
        String code = p.getMsgCode();
        String msg = p.getMsgInfo();
        Throwable ex = p.getException();
        String[] tags = p.getTags();
        String tagStr = tags == null || tags.length == 0 ? "" : "|" + Arrays.toString(tags);
        LogLevel level = getFalseResultLogLevel();
        level = ex != null ? LogLevel.ERROR : p.isSuccess() || level == LogLevel.INFO ? LogLevel.INFO : level;
        List<Object> logParamsList = new ArrayList<>(Arrays.asList(getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr));
        LogType logType = LogType.DIGEST;
        if (isDetail) {
            String input = formatLongText(p.getInput());
            String output = formatLongText(p.getOutput());
            logParamsList.add(input);
            logParamsList.add(output);
            logType = LogType.DETAIL;
        }
        String pattern = getLogPattern(p, logType);
        if (ex != null) {
            logParamsList.add(ex);
        }
        logWithLevel(logger, level, pattern, logParamsList);
    }


    @Override
    public void logDetail(MoniLogParams p) {
        logDetailOrDigest(p, true);
    }


    @Override
    public void logDigest(MoniLogParams p) {
        logDetailOrDigest(p, false);

    }

    @Override
    public void logLongRt(MoniLogParams p) {
        if (p == null) {
            return;
        }
        Logger logger = getLogger(p);
        String logPoint = p.getLogPoint().name();
        String service = p.getService();
        String action = p.getAction();
        String success = p.isSuccess() ? "true" : "false";
        String code = p.getMsgCode();
        String msg = p.getMsgInfo();
        String[] tags = p.getTags();
        String tagStr = tags == null || tags.length == 0 ? "" : "|" + Arrays.toString(tags);
        String rt = p.getCost() + "ms";
        logWithLevel(logger, getLogLevel(p, LogType.LONG_RT), getLogPattern(p, LogType.LONG_RT), getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr);
    }

    @Override
    public void logLargeSize(MoniLogParams p, String key, long sizeInBytes) {
        if (p == null || sizeInBytes <= 0) {
            return;
        }
        Logger logger = getLogger(p);
        String readableSize = RamUsageEstimator.humanReadableUnits(sizeInBytes);
        String rt = p.getCost() + "ms";
        logWithLevel(logger, getLogLevel(p, LogType.LARGE_SIZE), getLogPattern(p, LogType.LARGE_SIZE), getLogPrefix(), p.getLogPoint(), p.getService(), p.getAction(), key, readableSize, rt);
    }

    private LogLevel getFalseResultLogLevel() {
        MoniLogProperties.LogLevelConfig cfg = moniLogProperties.getPrinter().getLogLevel();
        LogLevel ll = (cfg == null ? new MoniLogProperties.LogLevelConfig() : cfg).getFalseResult();
        return ll == null ? LogLevel.ERROR : ll;
    }

    private LogLevel getLongRtLogLevel() {
        MoniLogProperties.LogLevelConfig cfg = moniLogProperties.getPrinter().getLogLevel();
        LogLevel ll = (cfg == null ? new MoniLogProperties.LogLevelConfig() : cfg).getLongRt();
        return ll == null ? LogLevel.ERROR : ll;
    }

    private LogLevel getLargeSizeLogLevel() {
        MoniLogProperties.LogLevelConfig cfg = moniLogProperties.getPrinter().getLogLevel();
        LogLevel ll = (cfg == null ? new MoniLogProperties.LogLevelConfig() : cfg).getLargeSize();
        return ll == null ? LogLevel.ERROR : ll;
    }

    private String formatLongText(Object o) {
        int maxTextLen = moniLogProperties.getPrinter().getMaxTextLen();
        if (o == null) {
            return null;
        }
        String str = JSON.toJSONString(o);
        if (str.length() > maxTextLen) {
            return str.substring(0, maxTextLen - 3) + "...";
        }
        return str;
    }
}