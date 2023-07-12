package com.jiduauto.log;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author yp
 * @date 2023/07/12
 */
@ConfigurationProperties("monitor.log")
@Getter
@Setter
public class MonitorLogProperties {
    @Value("${spring.application.name}")
    private String appName;

    private boolean enable;
    private boolean resetLogAppenders;
    private String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%level] %X{trace_id} %logger{35} - %msg%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}";
    private String logDir;
    private int maxLogHistory = 7;
    private String maxLogSize = "500MB";

    public String getLogDir() {
        if (StringUtils.isNotBlank(logDir)) {
            return logDir;
        }
        return "/app/logs/" + appName;
    }
}
