package com.jiduauto.monitor.log;


import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

/**
 * 默认日志打印方式
 */
class DefaultMonitorLogPrinter implements MonitorLogPrinter {
    @Resource
    private MonitorLogProperties monitorLogProperties;

    @Override
    public void log(MonitorLogParams logParams) {
        if (logParams == null) {
            return;
        }
        Class<?> serviceCls = logParams.getServiceCls();
        if (serviceCls == null) {
            serviceCls = MonitorLogUtil.class;
        }
        Logger logger = LoggerFactory.getLogger(serviceCls);
        String logPoint = logParams.getLogPoint().name();
        String service = logParams.getService();
        String action = logParams.getAction();
        String success = logParams.isSuccess() ? "true" : "false";
        String code = logParams.getMsgCode();
        String msg = logParams.getMsgInfo();
        String rt = logParams.getCost() + "ms";
        String input = formatLongText(logParams.getInput());
        String output = formatLongText(logParams.getOutput());
        Throwable ex = logParams.getException();

        if (!logParams.isSuccess()) {
            logger.error("monitor_log[{}]-{}.{} {}|{}|{} {} input:{}, output:{}", logPoint, service, action, success, code, msg, rt, input, output, ex);
            return;
        }
        String tags = JSON.toJSONString(logParams.getTags());
        logger.info("monitor_log[{}]-{}.{} {}|{}|{} {} input:{}, output:{}, tags:{}", logPoint, service, action, success, code, msg, rt, input, output, tags);
    }

    private String formatLongText(Object o) {
        int maxTextLen = monitorLogProperties.getPrinter().getMaxTextLen();
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