
package com.jiduauto.log.xxljobspringbootstarter;

import com.jiduauto.log.MonitorLogPrinter;
import com.jiduauto.log.xxljobspringbootstarter.interceptor.XxlJobLogMonitorExecuteInterceptor;
import com.xxl.job.core.handler.IJobHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(IJobHandler.class)
public class XxlJobLogMonitorConfiguration {
    public XxlJobLogMonitorConfiguration() {
    }

    @Bean
    public XxlJobLogMonitorExecuteInterceptor xxlJobExecuteInterceptor(MonitorLogPrinter processor) {
        return new XxlJobLogMonitorExecuteInterceptor(processor);
    }
}
