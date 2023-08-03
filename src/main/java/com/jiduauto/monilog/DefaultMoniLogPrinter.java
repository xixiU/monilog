package com.jiduauto.monilog;


import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

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
        Class<?> serviceCls = p.getServiceCls();
        if (serviceCls == null) {
            serviceCls = MoniLogUtil.class;
        }
        Logger logger = LoggerFactory.getLogger(serviceCls);
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

        if (!p.isSuccess()) {
            logger.error("monilog_detail_log[{}]-{}.{}|{}|{}|{}|{} input:{}, output:{}", logPoint, service, action, success, code, msg, rt, input, output, ex);
            return;
        }
        String tags = JSON.toJSONString(p.getTags());
        logger.info("monilog_detail_log[{}]-{}.{}|{}|{}|{}|{} input:{}, output:{}, tags:{}", logPoint, service, action, success, code, msg, rt, input, output, tags);
    }

    @Override
    public void logDigest(MoniLogParams p) {
        if (p == null) {
            return;
        }
        Class<?> serviceCls = p.getServiceCls();
        if (serviceCls == null) {
            serviceCls = MoniLogUtil.class;
        }
        Logger logger = LoggerFactory.getLogger(serviceCls);
        String logPoint = p.getLogPoint().name();
        String service = p.getService();
        String action = p.getAction();
        String success = p.isSuccess() ? "true" : "false";
        String code = p.getMsgCode();
        String msg = p.getMsgInfo();
        String rt = p.getCost() + "ms";
        if (!p.isSuccess()) {
            logger.error("monilog_digest_log[{}]-{}.{}|{}|{}|{}|{}", logPoint, service, action, success, code, msg, rt);
            return;
        }
        logger.info("monilog_digest_log[{}]-{}.{}|{}|{}|{}|{}", logPoint, service, action, success, code, msg, rt);
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