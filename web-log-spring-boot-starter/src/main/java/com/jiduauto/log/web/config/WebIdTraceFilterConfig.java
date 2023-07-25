package com.jiduauto.log.web.config;


import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
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

import static com.jiduauto.log.core.constant.Constants.TRACE_ID;

/**
 * @author qiang.zhang
 * @since 2023/5/5 14:31
 */
@Configuration
@ConditionalOnMissingBean(name = "initTraceIdFilter")
@ConditionalOnProperty(prefix = "monitor.log.web", name = "enable", havingValue = "true", matchIfMissing = true)
public class WebIdTraceFilterConfig {
    @Value("${spring.application.name}")
    private String appName;

    /**** Filter配置 ****/
    @Bean
    public FilterRegistrationBean<InitTraceIdFilter> initTraceIdFilter() {
        FilterRegistrationBean<InitTraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InitTraceIdFilter());
        registration.setName("initTraceIdFilter");
        registration.setOrder(-101);
        registration.addUrlPatterns("*");
        registration.addInitParameter("app", appName);
        return registration;
    }

    @Slf4j
    private static class InitTraceIdFilter extends OncePerRequestFilter {
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
