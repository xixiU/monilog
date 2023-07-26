package com.jiduauto.log.web;

import com.jiduauto.log.core.CoreMonitorLogConfiguration;
import com.jiduauto.log.core.util.SpringUtils;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "monitor.log.web", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('web')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('web'))")
@ConditionalOnClass({CoreMonitorLogConfiguration.class})
class WebMonitorLogConfiguration {
    @Bean
    @ConditionalOnMissingBean(name ="springUtils")
    public SpringUtils springUtils(){
        return new SpringUtils();
    }
    @Bean
    @ConditionalOnMissingBean(name = "logMonitorFilterBean")
    public FilterRegistrationBean<LogMonitorHandlerFilter> logMonitorFilterBean() {
        FilterRegistrationBean<LogMonitorHandlerFilter> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(new LogMonitorHandlerFilter());
        filterRegBean.setOrder(Integer.MAX_VALUE);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("log monitor filter");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }
}
