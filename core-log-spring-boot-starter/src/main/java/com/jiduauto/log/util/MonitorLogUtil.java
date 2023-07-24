package com.jiduauto.log.util;

import com.jiduauto.log.constant.Constants;
import com.jiduauto.log.enums.MonitorType;
import com.jiduauto.log.model.MonitorLogParams;
import com.metric.MetricMonitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @description: 日志工具类
 * @author rongjie.yuan
 * @date 2023/7/17 16:42
 */
@Slf4j
@Component
public class MonitorLogUtil {

    private static final String applicationName = SpringUtils.getApplicationName();

    public static void log(MonitorLogParams logParams){
        try{
            realLog(logParams);
        }catch (Exception e){
            log.error("log error", e);
        }
    }

    private static void realLog(MonitorLogParams logParams) {
        String[] tags = processTags(logParams);

        // 默认打一个record记录
        MetricMonitor.record(Constants.BUSINESS_NAME_PREFIX +  MonitorType.RECORD.getMark(), tags);
        // 对返回值添加累加记录
        MetricMonitor.cumulation(Constants.BUSINESS_NAME_PREFIX +  MonitorType.CUMULATION.getMark(), 1, tags);
        if (logParams.getCost()> 0L) {
            MetricMonitor.eventDruation(Constants.BUSINESS_NAME_PREFIX +  MonitorType.TIMER.getMark(), tags).record(logParams.getCost(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 统一打上环境标、应用名、打标类型、处理结果
     * @param logParams
     * @return
     */

    private static String[] processTags(MonitorLogParams logParams){
        String[] tags = logParams.getTags();
        ArrayList<String> tagList = new ArrayList<>();

        if (tags!= null && tags.length>0) {
            tagList = new ArrayList<>(Arrays.asList(tags));
        }
        tagList.add(Constants.RESULT);
        boolean success = logParams.isSuccess();
        if (!success || logParams.getException() != null) {
            tagList.add(Constants.ERROR);
        }else{
            tagList.add(Constants.SUCCESS);
        }
        if (StringUtils.isNotBlank(logParams.getMsgCode())) {
            tagList.add(Constants.MSG_CODE);
            tagList.add(logParams.getMsgCode());
        }
        tagList.add(Constants.APPLICATION);
        tagList.add(applicationName);
        tagList.add(Constants.LOG_POINT);
        tagList.add(logParams.getLogPoint().name());
        tagList.add(Constants.ENV);
        tagList.add(SpringUtils.getActiveProfile());
        if (logParams.getException() != null) {
            Throwable exception = logParams.getException();
            tagList.add(Constants.EXCEPTION);
            tagList.add(exception.getClass().getSimpleName());

            tagList.add(Constants.EXCEPTION_MSG);
            tagList.add(exception.getMessage());
        }

        if (StringUtils.isNotBlank(logParams.getService())) {
            tagList.add(Constants.SERVICE_NAME);
            tagList.add(logParams.getService());
        }

        if (StringUtils.isNotBlank(logParams.getAction())) {
            tagList.add(Constants.ACTION_NAME);
            tagList.add(logParams.getAction());
        }
        if (logParams.getCost()> 0L) {
            tagList.add(Constants.COST);
            tagList.add(String.valueOf(logParams.getCost()));
        }
        tags = tagList.toArray(new String[0]);
        return tags;
    }

}
