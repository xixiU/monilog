package com.jiduauto.log.core.model;

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
}
