package com.jiduauto.monilog;


import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;

import javax.annotation.Resource;
import java.util.Arrays;

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
            logger.error("{}detail_log[{}]-{}.{}|{}|{}|{}|{}{} input:{}, output:{}", getLogPrefix(), logPoint, service, action, success, code, msg, rt, tagStr, input, output, ex);
            return;
        }
        logger.info("{}detail_log[{}]-{}.{}|{}|{}|{}|{}{} input:{}, output:{}", getLogPrefix(), logPoint, service, action, success, code, msg, rt, tagStr, input, output);
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
            logger.error("{}digest_log[{}]-{}.{}|{}|{}|{}|{}{}", getLogPrefix(), logPoint, service, action, success, code, msg, rt, tagStr);
            return;
        }
        logger.info("{}digest_log[{}]-{}.{}|{}|{}|{}|{}{}", getLogPrefix(), logPoint, service, action, success, code, msg, rt, tagStr);
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