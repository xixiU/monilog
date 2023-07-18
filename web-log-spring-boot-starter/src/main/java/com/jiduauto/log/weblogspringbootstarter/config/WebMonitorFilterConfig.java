package com.jiduauto.log.weblogspringbootstarter.config;

import com.jiduauto.log.weblogspringbootstarter.filter.LogMonitorHandlerFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "monitor.log.web", name = "enable", havingValue = "true", matchIfMissing = true)
public class WebMonitorFilterConfig {
    @Bean
    @ConditionalOnMissingBean(LogMonitorHandlerFilter.class)
    public FilterRegistrationBean<LogMonitorHandlerFilter> logFilterBean() {
        FilterRegistrationBean<LogMonitorHandlerFilter> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(new LogMonitorHandlerFilter());
        filterRegBean.setOrder(-100);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("log filter");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }
}
