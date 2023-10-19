package com.jiduauto.monilog;


import com.alibaba.fastjson.JSON;
import com.carrotsearch.sizeof.RamUsageEstimator;
import org.slf4j.Logger;

import javax.annotation.Resource;
import java.util.Arrays;


/**
 * 默认日志打印方式
 */
class DefaultMoniLogPrinter implements MoniLogPrinter {
    private static final String DETAIL_LOG_PATTERN = "{}detail_log[{}]-{}.{}|{}|{}|{}|{}{} input:{}, output:{}";
    private static final String DIGEST_LOG_PATTERN = "{}digest_log[{}]-{}.{}|{}|{}|{}|{}{}";
    private static final String LONG_RT_LOG_PATTERN = "{}rt_too_long[{}]-{}.{}|{}|{}|{}|{}{}";
    private static final String LARGE_SIZE_LOG_PATTERN = "{}size_too_large[{}]-{}.{}[key={}], size: {}, rt:{}";
    @Resource
    private MoniLogProperties moniLogProperties;

    @Override
    public void logDetail(MoniLogParams p) {
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
        String rt = p.getCost() + "ms";
        String input = formatLongText(p.getInput());
        String output = formatLongText(p.getOutput());
        Throwable ex = p.getException();

        String[] tags = p.getTags();
        String tagStr = tags == null || tags.length == 0 ? "" : "|" + Arrays.toString(tags);
        if (ex != null) {
            logger.error(DETAIL_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr, input, output, ex);
            return;
        }
        LogLevel level = getFalseResultLogLevel();
        if (p.isSuccess() || level == LogLevel.INFO) {
            logger.info(DETAIL_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr, input, output);
            return;
        }
        if (level == LogLevel.ERROR) {
            logger.error(DETAIL_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr, input, output);
        } else {
            logger.warn(DETAIL_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr, input, output);
        }
    }

    @Override
    public void logDigest(MoniLogParams p) {
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
        if (p.getException() != null) {
            logger.error(DIGEST_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr);
            return;
        }
        LogLevel level = getFalseResultLogLevel();
        if (p.isSuccess() || level == LogLevel.INFO) {
            logger.info(DIGEST_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr);
            return;
        }
        if (level == LogLevel.ERROR) {
            logger.error(DIGEST_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr);
        } else {
            logger.warn(DIGEST_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr);
        }
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
        LogLevel level = getLongRtLogLevel();
        switch (level) {
            case INFO:
                logger.info(LONG_RT_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr);
                return;
            case WARN:
                logger.warn(LONG_RT_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr);
                return;
            case ERROR:
            default:
                logger.error(LONG_RT_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, rt, code, msg, tagStr);
        }
    }

    @Override
    public void logLargeSize(MoniLogParams p, String key, long sizeInBytes) {
        if (p == null || sizeInBytes <= 0) {
            return;
        }
        String readableSize = RamUsageEstimator.humanReadableUnits(sizeInBytes);
        String rt = p.getCost() + "ms";
        Logger logger = getLogger(p);
        LogLevel level = getLargeSizeLogLevel();
        switch (level) {
            case INFO:
                logger.info(LARGE_SIZE_LOG_PATTERN, getLogPrefix(), p.getLogPoint(), p.getService(), p.getAction(), key, readableSize, rt);
                return;
            case WARN:
                logger.warn(LARGE_SIZE_LOG_PATTERN, getLogPrefix(), p.getLogPoint(), p.getService(), p.getAction(), key, readableSize, rt);
                return;
            case ERROR:
            default:
                logger.error(LARGE_SIZE_LOG_PATTERN, getLogPrefix(), p.getLogPoint(), p.getService(), p.getAction(), key, readableSize, rt);
        }
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
            return "";
        }
        String str = JSON.toJSONString(o);
        if (str.length() > maxTextLen) {
            return str.substring(0, maxTextLen - 3) + "...";
        }
        return str;
    }
}