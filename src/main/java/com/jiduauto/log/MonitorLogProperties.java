package com.jiduauto.log;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author yp
 * @date 2023/07/12
 */
@ConfigurationProperties("monitor.log")
@Getter
@Setter
public class MonitorLogProperties {
    private boolean enable;
    private boolean resetLogAppenders;
    private String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%level] %X{trace_id} %logger{35} - %msg%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}";
    private String logDir = "/app/logs/${spring.application.name}";
    private int maxLogHistory = 7;
    private String maxLogSize = "500MB";


}
