package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @author rongjie.yuan
 * @date 2023/7/28 17:06
 */
@Configuration
@EnableConfigurationProperties(MoniLogProperties.class)
@ConditionalOnProperty(prefix = "monilog", name = "enable", matchIfMissing = true)
@Slf4j
@Import({SpringUtils.class})
class MoniLogAutoConfiguration {
    @Bean
    @ConditionalOnProperty(name = "spring.mvc.throw-exception-if-no-handler-found", havingValue = "true")
    MoniLogExceptionHandler moniLogExceptionHandler() {
        return new MoniLogExceptionHandler();
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    static SpringUtils moniLogSpringUtils() {
        return new SpringUtils();
    }

    @Bean
    @ConditionalOnProperty(prefix = "monilog.printer", name = "report-test-result")
    LogCollector moniLogTestReporter() {
        return new LogCollector();
    }

    @Bean
    @ConditionalOnMissingBean(MoniLogPrinter.class)
    MoniLogPrinter moniLogPrinter() {
        return new DefaultMoniLogPrinter();
    }

    @Bean
    @ConditionalOnBean(MoniLogPrinter.class)
    MoniLogAop moniLogAop() {
        log.info(">>>monilog core start...");
        return new MoniLogAop();
    }

    @ConditionalOnWebApplication
    @Bean
    FilterRegistrationBean<WebMoniLogInterceptor> webMoniLogInterceptor(MoniLogProperties moniLogProperties) {
        boolean webEnable = moniLogProperties.isComponentEnable(ComponentEnum.web, moniLogProperties.getWeb().isEnable());
        boolean feignEnable = moniLogProperties.isComponentEnable(ComponentEnum.feign, moniLogProperties.getFeign().isEnable());
        if (webEnable) {
            log.info(">>>monilog {} start...", ComponentEnum.web);
        }
        if (feignEnable) {
            log.info(">>>monilog {} start...", ComponentEnum.feign);
        }
        FilterRegistrationBean<WebMoniLogInterceptor> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(new WebMoniLogInterceptor(moniLogProperties));
        // 这个order顺序不能随便改
        filterRegBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10000);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("webMoniLogInterceptor");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }
}
