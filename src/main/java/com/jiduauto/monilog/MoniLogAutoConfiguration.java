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
import org.springframework.core.annotation.Order;

/**
 * @author rongjie.yuan
 * @description: 启动类
 * @date 2023/7/28 17:06
 */
@Configuration
@EnableConfigurationProperties(MoniLogProperties.class)
@ConditionalOnProperty(prefix = "monilog", name = "enable", matchIfMissing = true)
@Slf4j
@Import({SpringUtils.class})
class MoniLogAutoConfiguration {
    @Order(Integer.MIN_VALUE)
    @Bean("__springUtils")
    SpringUtils springUtils() {
        return new SpringUtils();
    }

    @Bean
    @ConditionalOnMissingBean(MoniLogPrinter.class)
    MoniLogPrinter moniLogPrinter() {
        return new DefaultMoniLogPrinter();
    }

    @Bean
    @ConditionalOnClass(name = {"org.springframework.data.redis.core.RedisTemplate"})
    MoniLogAppListener applicationPreparedListener() {
        log.info(">>>monilog redis start...");
        return new MoniLogAppListener();
    }

    @Bean
    MoniLogPostProcessor moniLogPostProcessor(MoniLogProperties moniLogProperties) {
        return new MoniLogPostProcessor(moniLogProperties);
    }

    @Bean
    @ConditionalOnBean(MoniLogPrinter.class)
    MoniLogAop aspectProcessor() {
        log.info(">>>monilog core start...");
        return new MoniLogAop();
    }

    @ConditionalOnProperty(prefix = "monilog.grpc", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('grpc')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('grpc'))")
    @ConditionalOnClass(name = {"io.grpc.stub.AbstractStub", "io.grpc.stub.ServerCalls"})
    @Configuration
    static class GrpcMoniLogConfiguration {
        @Order(-100)
        @GrpcGlobalServerInterceptor
        @ConditionalOnProperty(prefix = "monilog.grpc.server", name = "enable", havingValue = "true", matchIfMissing = true)
        GrpcMoniLogInterceptor.GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
            log.info(">>>monilog grpc-server start...");
            return new GrpcMoniLogInterceptor.GrpcLogPrintServerInterceptor();
        }

        @Order(-101)
        @GrpcGlobalClientInterceptor
        @ConditionalOnClass(name = "io.grpc.ClientInterceptor")
        @ConditionalOnProperty(prefix = "monilog.grpc.client", name = "enable", havingValue = "true", matchIfMissing = true)
        GrpcMoniLogInterceptor.GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
            log.info(">>>monilog grpc-client start...");
            return new GrpcMoniLogInterceptor.GrpcLogPrintClientInterceptor();
        }
    }

    @ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
    @ConditionalOnProperty(prefix = "monilog.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('mybatis')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('mybatis'))")
    @Bean
    MybatisMoniLogInterceptor.MybatisInterceptor mybatisMonitorSqlFilter() {
        log.info(">>>monilog mybatis start...");
        return new MybatisMoniLogInterceptor.MybatisInterceptor();
    }

    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "monilog.web", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('web')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('web'))")
    @Bean
    FilterRegistrationBean<WebMoniLogInterceptor> webMoniLogInterceptor(MoniLogProperties moniLogProperties) {
        log.info(">>>monilog web start...");
        FilterRegistrationBean<WebMoniLogInterceptor> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(new WebMoniLogInterceptor(moniLogProperties));
        filterRegBean.setOrder(Integer.MAX_VALUE);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("webMoniLogInterceptor");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }

    @ConditionalOnProperty(prefix = "monilog.xxljob", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('xxljob')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('xxljob'))")
    @ConditionalOnClass(name = "com.xxl.job.core.handler.IJobHandler")
    @Bean
    XxlJobMoniLogInterceptor xxljobMoniLogInterceptor() {
        log.info(">>>monilog xxljob start...");
        return new XxlJobMoniLogInterceptor();
    }

    @ConditionalOnProperty(prefix = "monilog.redis", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('redis')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('redis'))")
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    @Bean
    RedisMoniLogInterceptor.RedissonInterceptor redissonInterceptor(MoniLogProperties moniLogProperties) {
        log.info(">>>monilog redis[redisson] start...");
        //redisson是异步api，拦截操作较为复杂，需要分步进行。通过下面的这个Interceptor的AOP来实现
        return new RedisMoniLogInterceptor.RedissonInterceptor(moniLogProperties.getRedis());
    }
}
