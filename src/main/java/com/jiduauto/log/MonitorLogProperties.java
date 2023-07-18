package com.jiduauto.log;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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
