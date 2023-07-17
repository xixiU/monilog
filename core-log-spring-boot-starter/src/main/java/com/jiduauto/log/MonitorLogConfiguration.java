package com.jiduauto.log;

import com.jiduauto.log.aop.MonitorLogAop;
import com.jiduauto.log.model.MonitorLogProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author yp
 * @date 2023/07/12
 */
@Configuration
@EnableConfigurationProperties(MonitorLogProperties.class)
@ConditionalOnProperty(prefix = "monitor.log", name = "enable", matchIfMissing = true)
public class MonitorLogConfiguration {
    @Bean
    @ConditionalOnBean(MonitorLogPrinter.class)
    public MonitorLogAop aspectProcessor(MonitorLogPrinter processor) {
        return new MonitorLogAop(processor);
    }
}
