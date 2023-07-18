package com.jiduauto.log.util;

import com.jiduauto.log.constant.Constants;
import com.jiduauto.log.enums.MonitorType;
import com.jiduauto.log.model.MonitorLogParams;
import com.metric.MetricMonitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class MonitorUtil {

    /**
     * 获取应用名称
     */
    private static String applicationName;

    @Value("${spring.application.name}")
    @Autowired
    public void setEnv(String applicationName) throws Exception {
        if (StringUtils.isBlank(applicationName)) {
            throw new Exception("spring.application.name must be specify");
        }
        applicationName = applicationName;
    }

    public static void log(MonitorLogParams logParams){
        try{
            realLog(logParams);
        }catch (Exception e){
            log.error("log error", e);
        }
    }

    private static void realLog(MonitorLogParams logParams) {
        MonitorType[] monitorTypes = logParams.getMonitorTypes();
        ArrayList<String> tagList = new ArrayList<>();
        String[] tags = logParams.getTags();

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
        tagList.add(Constants.APPNAME);
        tagList.add(applicationName);
        tags = CollectionUtils.isNotEmpty(tagList) ? tagList.toArray(tags) : null;
        for (MonitorType monitorType : monitorTypes) {
            String name = StringUtils.join(Constants.DOT, applicationName, logParams.getLogPoint().name(), logParams.getService() , logParams.getServiceCls().getName()) + monitorType.getMark();
            // 默认打一个record记录
            MetricMonitor.record(name, tags);

            if (MonitorType.TIMER.equals(monitorType)) {
                MetricMonitor.eventDruation(name, tags).record(logParams.getCost(), TimeUnit.MILLISECONDS);
            }
            // 对返回值添加累加记录
            MetricMonitor.cumulation(name, 1, tags);
        }
    }


}
