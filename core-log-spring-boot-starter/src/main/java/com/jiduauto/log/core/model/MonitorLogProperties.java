package com.jiduauto.log.core.model;

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

    @Value("${spring.profiles.active}")
    private String profiles;

    @Value("${app.id}")
    private String appId;

    private boolean enable;
    private boolean resetLogAppenders;
    private String pattern = "%date{yyyy-MM-dd HH:mm:ss.SSS} ${OTEL_SERVICE_NAME} ${hostName} %level [%thread] %logger{36} %M [%line] [%X{trace_id}] [%X{span_id}] %msg\\n";
    private String logDir;
    private int maxLogHistory = 7;
    private String maxLogSize = "500MB";

    public String getLogDir() {
        if (StringUtils.isNotBlank(logDir)) {
            return logDir;
        }
        String serviceName;
        if (StringUtils.isNotBlank(appName)) {
            serviceName = appName;
        } else {
            serviceName = appId;
        }
        String baseDir = "local".equalsIgnoreCase(profiles) ? System.getProperty("user.dir") : "/app/logs/";
        return baseDir + serviceName;
    }
}
