package com.jiduauto.monilog;


import com.alibaba.fastjson.JSON;
import com.carrotsearch.sizeof.RamUsageEstimator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import javax.annotation.Resource;
import java.util.Arrays;

import static com.carrotsearch.sizeof.RamUsageEstimator.ONE_KB;


/**
 * 默认日志打印方式
 */
class DefaultMoniLogPrinter implements MoniLogPrinter {
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
            logger.error("{}detail_log[{}]-{}.{}|{}|{}|{}|{}{} input:{}, output:{}", getLogPrefix(), logPoint, service, action, success, code, rt, msg,  tagStr, input, output, ex);
            return;
        }
        logger.info("{}detail_log[{}]-{}.{}|{}|{}|{}|{}{} input:{}, output:{}", getLogPrefix(), logPoint, service, action, success, code, rt, msg,  tagStr, input, output);
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
            logger.error("{}digest_log[{}]-{}.{}|{}|{}|{}|{}{}", getLogPrefix(), logPoint, service, action, success, code, rt, msg,  tagStr);
            return;
        }
        logger.info("{}digest_log[{}]-{}.{}|{}|{}|{}|{}{}", getLogPrefix(), logPoint, service, action, success, code, rt, msg,  tagStr);
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
        logger.error("{}rt_too_long[{}]-{}.{}|{}|{}|{}|{}{}", getLogPrefix(), logPoint, service, action, success, code, rt, msg,  tagStr);
    }

    @Override
    public void logLargeSize(MoniLogParams p, String key) {
        if (p == null || p.getLogPoint() != LogPoint.redis || p.getOutput() == null || StringUtils.isBlank(key)) {
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
        Logger logger = getLogger(p);
        if (valueLen > 0 && valueLen > redisConf.getWarnForValueLength() * ONE_KB) {
            logger.error("{}size_too_large[{}]-{}.{}[key={}], size: {}", getLogPrefix(), p.getLogPoint(), p.getService(), p.getAction(), key, RamUsageEstimator.humanReadableUnits(valueLen));
        }
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