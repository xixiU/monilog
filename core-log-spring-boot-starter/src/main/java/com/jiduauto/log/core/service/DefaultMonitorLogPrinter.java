package com.jiduauto.log.core.service;

import com.alibaba.fastjson.JSON;
import com.jiduauto.log.core.MonitorLogPrinter;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author rongjie.yuan
 * @description: 默认日志打印方式
 * @date 2023/7/25 20:55
 */
public class DefaultMonitorLogPrinter implements MonitorLogPrinter {

    @Value("${monitor.log.printer.text.len.max:5000}")
    private int maxTextLen = 5000;

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
        String success = logParams.isSuccess() ? "Y" : "N";
        String code = logParams.getMsgCode();
        String msg = logParams.getMsgInfo();
        String rt = logParams.getCost() + "ms";
        String input = formatLongText(logParams.getInput());
        String output = formatLongText(logParams.getOutput());
        Throwable ex = logParams.getException();

        if (!logParams.isSuccess()) {
            logger.error("monitorlog[{}]-{}.{} {}-{}-{} {} input:{}, output:{}", logPoint, service, action, success, code, msg, rt, input, output, ex);
            return;
        }
        String tags = JSON.toJSONString(MonitorLogUtil.processTags(logParams));
        logger.error("monitorlog[{}]-{}.{} {}-{}-{} {} input:{}, output:{}, tags:{}", logPoint, service, action, success, code, msg, rt, input, output, tags);
    }

    private String formatLongText(Object o) {
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
