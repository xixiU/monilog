
package com.jiduauto.log.xxljob;

import com.jiduauto.log.xxljob.interceptor.XxlJobLogMonitorExecuteInterceptor;
import com.xxl.job.core.handler.IJobHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(IJobHandler.class)
@ConditionalOnProperty(prefix = "monitor.log.xxljob", name = "enable", havingValue = "true", matchIfMissing = true)
public class XxlJobLogMonitorConfiguration {

    @Bean
    public XxlJobLogMonitorExecuteInterceptor xxlJobExecuteInterceptor() {
        return new XxlJobLogMonitorExecuteInterceptor();
    }
}
