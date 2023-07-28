package com.jiduauto.monitor.log.util;

import com.alibaba.fastjson.JSON;
import com.jiduauto.monitor.log.constant.Constants;
import com.jiduauto.monitor.log.enums.LogPoint;
import com.jiduauto.monitor.log.enums.MonitorType;
import com.jiduauto.monitor.log.model.MonitorLogParams;
import com.jiduauto.monitor.log.model.MonitorLogPrinter;
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
        TagBuilder systemTags = getSystemTags(logParams);
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            logPoint = LogPoint.unknown;
        }
        //TODO 这里在后边加入了自定义的tag，可能与全局监控混淆
        String[] allTags = systemTags.add(logParams.getTags()).toArray();

        String name = Constants.BUSINESS_NAME_PREFIX + Constants.UNDERLINE;
        if (logParams.isHasUserTag()) {
            name = name + logParams.getService() + Constants.UNDERLINE + logParams.getAction();
        }
        name = name + Constants.UNDERLINE + logPoint.name();
        // 默认打一个record记录
        MetricMonitor.record(name + MonitorType.RECORD.getMark(), allTags);
        // 对返回值添加累加记录
        MetricMonitor.cumulation(name + MonitorType.CUMULATION.getMark(), 1, allTags);
        try {
            MetricMonitor.eventDruation(name + MonitorType.TIMER.getMark(), allTags).record(logParams.getCost(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("MetricMonitor.eventDuration name:{}, tag:{}", name, JSON.toJSONString(allTags));
        }
    }

    /**
     * 统一打上环境标、应用名、打标类型、处理结果
     */
    private static TagBuilder getSystemTags(MonitorLogParams logParams) {
        boolean success = logParams.isSuccess() && logParams.getException() == null;
        String exceptionMsg = logParams.getException() == null ? "null" : ExceptionUtil.getErrorMsg(logParams.getException());
        int maxLen = 30;
        if (exceptionMsg.length() > maxLen) {
            exceptionMsg = exceptionMsg.substring(0, maxLen) + "...";
        }
        return TagBuilder.of(Constants.RESULT, success ? Constants.SUCCESS : Constants.ERROR)
                .add(Constants.APPLICATION, MonitorSpringUtils.getApplicationName())
                .add(Constants.LOG_POINT, logParams.getLogPoint().name())
                .add(Constants.ENV, MonitorSpringUtils.getActiveProfile())
                .add(Constants.SERVICE_NAME, logParams.getService())
                .add(Constants.ACTION_NAME, logParams.getAction())
                .add(Constants.MSG_CODE, logParams.getMsgCode())
                .add(Constants.COST, String.valueOf(logParams.getCost()))
                .add(Constants.EXCEPTION, exceptionMsg);
    }
}
