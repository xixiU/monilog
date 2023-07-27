package com.jiduauto.log.core.util;

import com.jiduauto.log.core.MonitorLogPrinter;
import com.jiduauto.log.core.constant.Constants;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.enums.MonitorType;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.metric.MetricMonitor;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;

/**
 * @author rongjie.yuan
 * @description: 日志工具类
 * @date 2023/7/17 16:42
 */
@Slf4j
public class MonitorLogUtil {
    public static void log(MonitorLogParams logParams) {
        MonitorLogPrinter printer = null;
        try {
            printer = MonitorSpringUtils.getBean(MonitorLogPrinter.class);
        } catch (Exception e) {
            log.error("no MonitorLogPrinter instance found");
        }

        try {
            doMonitor(logParams);
            if (printer != null) {
                printer.log(logParams);
            }
        } catch (Exception e) {
            log.error("log error", e);
        }
    }

    private static void doMonitor(MonitorLogParams logParams) {
        String[] tags = processTags(logParams);
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            logPoint = LogPoint.UNKNOWN_ENTRY;
        }
        String name = Constants.BUSINESS_NAME_PREFIX + Constants.UNDERLINE + logPoint.name();
        // 默认打一个record记录
        MetricMonitor.record(name + MonitorType.RECORD.getMark(), tags);
        // 对返回值添加累加记录
        MetricMonitor.cumulation(name + MonitorType.CUMULATION.getMark(), 1, tags);
        MetricMonitor.eventDruation(name + MonitorType.TIMER.getMark(), tags).record(logParams.getCost(), TimeUnit.MILLISECONDS);
    }

    /**
     * 统一打上环境标、应用名、打标类型、处理结果
     */
    public static String[] processTags(MonitorLogParams logParams) {
        TagBuilder tb = TagBuilder.of(logParams.getTags());
        boolean success = logParams.isSuccess() && logParams.getException() == null;
        tb.add(Constants.RESULT, success ? Constants.SUCCESS : Constants.ERROR)
                .add(Constants.APPLICATION, MonitorSpringUtils.getApplicationName())
                .add(Constants.LOG_POINT, logParams.getLogPoint().name())
                .add(Constants.ENV, MonitorSpringUtils.getActiveProfile())
                .add(Constants.SERVICE_NAME, logParams.getService())
                .add(Constants.ACTION_NAME, logParams.getAction())
                .add(Constants.MSG_CODE, logParams.getMsgCode())
                .add(Constants.COST, String.valueOf(logParams.getCost()));
        if (logParams.getException() != null) {
            Throwable exception = logParams.getException();
            tb.add(Constants.EXCEPTION, exception.getClass().getSimpleName()).add(Constants.EXCEPTION_MSG, exception.getMessage());
        }
        return tb.toArray();
    }
}
