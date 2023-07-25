package com.jiduauto.log.core;

import com.jiduauto.log.core.aop.MonitorLogAop;
import com.jiduauto.log.core.model.MonitorLogProperties;
import com.jiduauto.log.core.service.DefaultMonitorLogPrinter;
import com.jiduauto.log.core.util.SpringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author yp
 * @date 2023/07/12
 */
@Configuration
@EnableConfigurationProperties(MonitorLogProperties.class)
@ConditionalOnProperty(prefix = "monitor.log", name = "enable", matchIfMissing = true)
@Import({SpringUtils.class})
public class MonitorLogConfiguration {

    @Bean
    @ConditionalOnBean(MonitorLogPrinter.class)
    public MonitorLogAop aspectProcessor(MonitorLogPrinter monitorLogPrinter) {
        return new MonitorLogAop(monitorLogPrinter);
    }

    @ConditionalOnMissingBean(MonitorLogPrinter.class)
    public MonitorLogPrinter monitorLogPrinter() {
        return new DefaultMonitorLogPrinter();
    }
}
