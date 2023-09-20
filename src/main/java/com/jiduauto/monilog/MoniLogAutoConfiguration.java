package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
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

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Bean("__springUtils")
    static SpringUtils springUtils() {
        return new SpringUtils();
    }

    @Bean
    @ConditionalOnProperty(prefix = "monilog.printer", name = "report-test-result")
    LogCollector testReporter() {
        return new LogCollector();
    }

    @Bean
    @ConditionalOnMissingBean(MoniLogPrinter.class)
    MoniLogPrinter moniLogPrinter() {
        return new DefaultMoniLogPrinter();
    }

    @Bean
    @ConditionalOnBean(MoniLogPrinter.class)
    MoniLogAop aspectProcessor() {
        log.info(">>>monilog core start...");
        return new MoniLogAop();
    }

    @ConditionalOnClass(name = {"io.grpc.stub.AbstractStub", "io.grpc.stub.ServerCalls"})
    @Configuration
    static class GrpcMoniLogConfiguration {
        @Order(-100)
        @GrpcGlobalServerInterceptor
        @ConditionalOnClass(name = "io.grpc.stub.ServerCalls")
        GrpcMoniLogInterceptor.GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
            log.info(">>>monilog grpc-server start...");
            return new GrpcMoniLogInterceptor.GrpcLogPrintServerInterceptor();
        }

        @Order(-101)
        @GrpcGlobalClientInterceptor
        @ConditionalOnClass(name = "io.grpc.ClientInterceptor")
        GrpcMoniLogInterceptor.GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
            log.info(">>>monilog grpc-client start...");
            return new GrpcMoniLogInterceptor.GrpcLogPrintClientInterceptor();
        }
    }

    @ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
    @Bean
    MybatisMoniLogInterceptor.MybatisInterceptor mybatisMonitorSqlFilter() {
        log.info(">>>monilog mybatis start...");
        return new MybatisMoniLogInterceptor.MybatisInterceptor();
    }

    @ConditionalOnWebApplication
    @Bean
    FilterRegistrationBean<WebMoniLogInterceptor> webMoniLogInterceptor(MoniLogProperties moniLogProperties) {
        boolean webEnable = moniLogProperties.isComponentEnable("web", moniLogProperties.getWeb().isEnable());
        boolean feignEnable = moniLogProperties.isComponentEnable("feign", moniLogProperties.getFeign().isEnable());
        if (webEnable) {
            log.info(">>>monilog web start...");
        }
        if (feignEnable) {
            log.info(">>>monilog feign start...");
        }
        FilterRegistrationBean<WebMoniLogInterceptor> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(new WebMoniLogInterceptor(moniLogProperties));
        // 这个order顺序不能随便改
        filterRegBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10000);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("webMoniLogInterceptor");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        filterRegBean.setEnabled(webEnable || feignEnable);
        return filterRegBean;
    }

    @ConditionalOnClass(name = "com.xxl.job.core.handler.IJobHandler")
    @Bean
    XxlJobMoniLogInterceptor xxljobMoniLogInterceptor() {
        log.info(">>>monilog xxljob start...");
        return new XxlJobMoniLogInterceptor();
    }

    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    @Bean
    RedisMoniLogInterceptor.RedissonInterceptor redissonInterceptor() {
        log.info(">>>monilog redis[redisson] start...");
        return new RedisMoniLogInterceptor.RedissonInterceptor();
    }

    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @Bean
    RedisMoniLogInterceptor.RedisConnectionInterceptor redisInterceptor() {
        log.info(">>>monilog redis[jedis] start...");
        return new RedisMoniLogInterceptor.RedisConnectionInterceptor();
    }
}
