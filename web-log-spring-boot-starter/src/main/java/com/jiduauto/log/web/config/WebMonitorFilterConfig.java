package com.jiduauto.log.web.config;

import com.jiduauto.log.core.util.SpringUtils;
import com.jiduauto.log.web.filter.LogMonitorHandlerFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "monitor.log.web", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.endpoints.include:*}'.equals('*') or '${monitor.log.endpoints.include}'.contains('web')) and !('${monitor.log.endpoints.exclude:}'.equals('*') or '${monitor.log.endpoints.exclude:}'.contains('web'))")
public class WebMonitorFilterConfig {
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
