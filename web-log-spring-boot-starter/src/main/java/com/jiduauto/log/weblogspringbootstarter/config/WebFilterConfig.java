package com.jiduauto.log.weblogspringbootstarter.config;


import com.jiduauto.log.weblogspringbootstarter.filter.LogMonitorHandlerFilter;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * @author qiang.zhang
 * @since 2023/5/5 14:31
 */
@Configuration
@ConditionalOnProperty(prefix = "monitor.log.web", name = "enable", havingValue = "true", matchIfMissing = true)
public class WebFilterConfig {

    /**** Filter配置 ****/
    @Bean
    @ConditionalOnMissingBean(InitTraceIdFilter.class)
    public FilterRegistrationBean<InitTraceIdFilter> initTraceIdFilter() {
        FilterRegistrationBean<InitTraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InitTraceIdFilter());
        registration.setName("initTraceIdFilter");
        registration.setOrder(-101);
        registration.addUrlPatterns("*");
        registration.addInitParameter("app", "crm-auth-service");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(LogMonitorHandlerFilter.class)
    public FilterRegistrationBean<LogMonitorHandlerFilter> logFilterBean() {
        final FilterRegistrationBean<LogMonitorHandlerFilter> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(new LogMonitorHandlerFilter());
        filterRegBean.setOrder(-100);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("log filter");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }

    @Slf4j
    private static class InitTraceIdFilter extends OncePerRequestFilter {
        private static final String TRACE_ID = "trace_id";

        @Override
        public void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain) throws IOException, ServletException {
            MDC.put(TRACE_ID, getTraceId(request));
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(TRACE_ID);
            }
        }

        private static String getTraceId(HttpServletRequest request) {
            String traceId = Span.current().getSpanContext().getTraceId();
            if (StringUtils.isBlank(traceId)) {
                traceId = request.getHeader(TRACE_ID);
            }
            if (StringUtils.isBlank(traceId)) {
                traceId = MDC.get(TRACE_ID);
            }
            if (StringUtils.isBlank(traceId)) {
                traceId = UUID.randomUUID().toString().toLowerCase().replaceAll("-", "");
            }
            return traceId;
        }

        @Override
        public void destroy() {
            MDC.remove(TRACE_ID);
        }
    }
}
