package com.jiduauto.monitor.log;

import com.alibaba.fastjson.JSON;
import com.metric.MetricMonitor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * @author rongjie.yuan
 * @description: 日志工具类
 * @date 2023/7/17 16:42
 */
@Slf4j
class MonitorLogUtil {
    public static void log(MonitorLogParams logParams) {
        try {
            doMonitor(logParams);
        } catch (Exception e) {
            log.warn(Constants.SYSTEM_ERROR_PREFIX + "doMonitor error:{}", e.getMessage());
        }
        try {
            printDetailLog(logParams);
        } catch (Exception e) {
            log.warn(Constants.SYSTEM_ERROR_PREFIX + "printDetailLog error:{}", e.getMessage());
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

        String name = Constants.BUSINESS_NAME_PREFIX ;
        if (logParams.isHasUserTag()) {
            name = name + Constants.UNDERLINE + logParams.getService() + Constants.UNDERLINE + logParams.getAction();
        }
        // TODO rongjie.yuan  2023/7/28 12:45 全局监控与业务监控区分开来。
        name = name + Constants.UNDERLINE + logPoint.name();
        // 默认打一个record记录
        MetricMonitor.record(name + MonitorType.RECORD.getMark(), allTags);
        // 对返回值添加累加记录
        MetricMonitor.cumulation(name + MonitorType.CUMULATION.getMark(), 1, allTags);
        try {
            MetricMonitor.eventDruation(name + MonitorType.TIMER.getMark(), allTags).record(logParams.getCost(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn(Constants.SYSTEM_ERROR_PREFIX + "eventDuration name:{}, tag:{}, error:{}", name, JSON.toJSONString(allTags), e.getMessage());
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

    /**
     * 打印详情日志
     * @param logParams
     */
    private static void printDetailLog(MonitorLogParams logParams){
        MonitorLogPrinter printer = null;
        MonitorLogProperties properties = null;
        try {
            printer = MonitorSpringUtils.getBean(MonitorLogPrinter.class);
            properties = MonitorSpringUtils.getBean(MonitorLogProperties.class);
        } catch (Exception e) {
            log.warn(Constants.SYSTEM_ERROR_PREFIX + ":no MonitorLogPrinter instance found");
        }
        if (printer == null || properties == null) {
            return;
        }
        MonitorLogProperties.PrinterProperties printerCfg = properties.getPrinter();
        if (!printerCfg.isPrintDetailLog()) {
            return;
        }
        LogPoint logPoint = logParams.getLogPoint();
        if (logPoint == null) {
            return;
        }
        boolean doPrinter = true;
        switch (logPoint) {
            case http_server: doPrinter = properties.getWeb().isPrintHttpServerDetailLog();break;
            case http_client: doPrinter = properties.getWeb().isPrintHttpClientDetailLog();break;
            case feign_server: doPrinter = properties.getFeign().isPrintFeignServerDetailLog();break;
            case feign_client: doPrinter = properties.getFeign().isPrintFeignClientDetailLog();break;
            case grpc_client: doPrinter = properties.getGrpc().isPrintGrpcClientDetailLog();break;
            case grpc_server: doPrinter = properties.getGrpc().isPrintGrpcServerDetailLog();break;
            case rocketmq_consumer: doPrinter = properties.getRocketmq().isPrintRocketmqConsumerDetailLog();break;
            case rocketmq_producer: doPrinter = properties.getRocketmq().isPrintRocketmqProducerDetailLog();break;
            case mybatis: doPrinter = properties.getMybatis().isPrintMybatisDetailLog();break;
            case xxljob: doPrinter = properties.getXxljob().isPrintXxljobDetailLog();break;
            case redis: doPrinter = properties.getRedis().isPrintRedisDetailLog();
            case unknown:
            default:
                break;
        }
        if (doPrinter) {
            printer.log(logParams);
        }
    }
}
