package com.jiduauto.log.core.service;

import com.alibaba.fastjson.JSON;
import com.jiduauto.log.core.MonitorLogPrinter;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author rongjie.yuan
 * @description: 默认日志打印方式
 * @date 2023/7/25 20:55
 */
public class DefaultMonitorLogPrinter implements MonitorLogPrinter {

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
        if (!logParams.isSuccess() || logParams.getException() != null) {
            logger.error("logPoint:{} service:{} action:{} input:{} has error", logParams.getLogPoint(),
                    logParams.getService(), logParams.getAction(), logParams.getInput(), logParams.getException());
        }
        String[] tags = MonitorLogUtil.processTags(logParams);
        logger.info("logPoint:{} service:{} action:{} input:{} tag:{}",
                logParams.getLogPoint(), logParams.getService(), logParams.getAction(), logParams.getInput(), JSON.toJSONString(tags));
    }
}
