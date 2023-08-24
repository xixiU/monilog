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
    private static final String LARGE_SIZE_LOG_PATTERN = "{}size_too_large[{}]-{}.{}[key={}], size: {}";
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
            logger.error(DETAIL_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, code, rt, msg, tagStr, input, output, ex);
            return;
        }
        logger.info(DETAIL_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, code, rt, msg, tagStr, input, output);
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
            logger.error(DIGEST_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, code, rt, msg, tagStr);
            return;
        }
        logger.info(DIGEST_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, code, rt, msg, tagStr);
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
        logger.error(LONG_RT_LOG_PATTERN, getLogPrefix(), logPoint, service, action, success, code, rt, msg, tagStr);
    }

    @Override
    public void logLargeSize(MoniLogParams p, String key, long sizeInBytes) {
        if (p == null || sizeInBytes <= 0) {
            return;
        }
        String readableSize = RamUsageEstimator.humanReadableUnits(sizeInBytes);
        getLogger(p).error(LARGE_SIZE_LOG_PATTERN, getLogPrefix(), p.getLogPoint(), p.getService(), p.getAction(), key, readableSize);
    }

    private String formatLongText(Object o) {
        int maxTextLen = moniLogProperties.getPrinter().getMaxTextLen();
        if (o == null || o instanceof String) {
            return (String) o;
        }
        String str = JSON.toJSONString(o);
        if (str.length() > maxTextLen) {
            return str.substring(0, maxTextLen - 3) + "...";
        }
        return str;
    }
}