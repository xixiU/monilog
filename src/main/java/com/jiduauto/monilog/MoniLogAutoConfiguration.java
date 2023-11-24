package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.boot.autoconfigure.condition.*;
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
    MoniLogPostProcessor moniLogPostProcessor(MoniLogProperties moniLogProperties) {
        return new MoniLogPostProcessor(moniLogProperties);
    }

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

    @Configuration
    static class GrpcMoniLogConfiguration {
        @Order(-200)
        @GrpcGlobalServerInterceptor
        @ConditionalOnClass(name = "io.grpc.stub.ServerCalls")
        GrpcMoniLogInterceptor.GrpcLogPrintServerInterceptor grpcMoniLogPrintServerInterceptor() {
            log.info(">>>monilog {} start...", ComponentEnum.grpc_server);
            return new GrpcMoniLogInterceptor.GrpcLogPrintServerInterceptor();
        }

        @Order
        @GrpcGlobalClientInterceptor
        @ConditionalOnClass(name = "io.grpc.ClientInterceptor")
        GrpcMoniLogInterceptor.GrpcLogPrintClientInterceptor grpcMoniLogPrintClientInterceptor() {
            log.info(">>>monilog {} start...", ComponentEnum.grpc_client);
            return new GrpcMoniLogInterceptor.GrpcLogPrintClientInterceptor();
        }
    }

    @ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
    @Bean
    @ConditionalOnProperty(prefix = "monilog.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
    FactoryBean<MybatisInterceptor> moniLogFactoryBean() {
        log.info(">>>monilog {} start...", ComponentEnum.mybatis);
        return new FactoryBean<MybatisInterceptor>() {
            @Override
            public MybatisInterceptor getObject() throws Exception {
                return new MybatisInterceptor();
            }

            @Override
            public Class<?> getObjectType() {
                return MybatisInterceptor.class;
            }
        };
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

    @ConditionalOnClass(name = "com.xxl.job.core.handler.IJobHandler")
    @Bean
    XxlJobMoniLogInterceptor xxljobMoniLogInterceptor() {
        log.info(">>>monilog {} start...", ComponentEnum.xxljob);
        return new XxlJobMoniLogInterceptor();
    }
}
