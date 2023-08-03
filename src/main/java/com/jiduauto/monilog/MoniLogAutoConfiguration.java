package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
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
    @Bean
    MoniLogProperties moniLogProperties(){
        return new MoniLogProperties();
    }

    @Bean
    @ConditionalOnBean(MoniLogPrinter.class)
    MoniLogAop aspectProcessor() {
        return new MoniLogAop();
    }

    @Bean
    @ConditionalOnMissingBean(MoniLogAop.class)
    @ConditionalOnClass(MoniLogTags.class)
    MoniLogAop aspectUserProcessor() {
        return new MoniLogAop();
    }

    @Order(Integer.MIN_VALUE)
    @Bean("__springUtils")
    SpringUtils springUtils() {
        return new SpringUtils();
    }

    @Bean
    @ConditionalOnMissingBean(MoniLogPrinter.class)
    MoniLogPrinter moniLogPrinter() {
        log.info("!!! monilog logPrinter start ...");
        return new DefaultMoniLogPrinter();
    }

//    @ConditionalOnProperty(prefix = "monilog.feign", name = "enable", havingValue = "true", matchIfMissing = true)
//    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('feign')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('feign'))")
//    @ConditionalOnClass(name = {"feign.Feign"})
//    @Bean
//    FeignMonitorInterceptor feignMonitorInterceptor() {
//        log.info("!!! feign monilog start ...");
//        return new FeignMonitorInterceptor();
//    }

    @ConditionalOnProperty(prefix = "monilog.grpc", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('grpc')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('grpc'))")
    @ConditionalOnClass(name = {"io.grpc.stub.AbstractStub", "io.grpc.stub.ServerCalls"})
    @Configuration
    static class GrpcMoniLogConfiguration {
        @Order(-100)
        @GrpcGlobalServerInterceptor
        @ConditionalOnProperty(prefix = "monilog.grpc.server", name = "enable", havingValue = "true", matchIfMissing = true)
        GrpcMoniLogInterceptor.GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
            log.info("!!! grpc server monilog start ...");
            return new GrpcMoniLogInterceptor.GrpcLogPrintServerInterceptor();
        }

        @Order(-101)
        @GrpcGlobalClientInterceptor
        @ConditionalOnClass(name = "io.grpc.ClientInterceptor")
        @ConditionalOnProperty(prefix = "monilog.grpc.client", name = "enable", havingValue = "true", matchIfMissing = true)
        GrpcMoniLogInterceptor.GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
            log.info("!!! grpc client monilog start ...");
            return new GrpcMoniLogInterceptor.GrpcLogPrintClientInterceptor();
        }
    }

    @ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
    @ConditionalOnProperty(prefix = "monilog.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('mybatis')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('mybatis'))")
    @Bean
    MybatisMoniLogInterceptor.MybatisInterceptor mybatisMonitorSqlFilter() {
        log.info("!!! mybatis monilog start ...");
        return new MybatisMoniLogInterceptor.MybatisInterceptor();
    }


    @Configuration
    @ConditionalOnProperty(prefix = "monilog.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('rocketmq')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('rocketmq'))")
    @ConditionalOnClass(name = {"org.apache.rocketmq.client.MQAdmin"})
    static class RocketMqMoniLogConfiguration {
        @Bean
        @ConditionalOnProperty(prefix = "monilog.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
        RocketMqMoniLogInterceptor.RocketMQConsumerEnhanceProcessor rocketMQConsumerPostProcessor() {
            log.info("!!! rocketmq consumer monilog start ...");
            return new RocketMqMoniLogInterceptor.RocketMQConsumerEnhanceProcessor();
        }

        @Bean
        @ConditionalOnProperty(prefix = "monilog.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
        RocketMqMoniLogInterceptor.RocketMQProducerInhanceProcessor rocketMQProducerPostProcessor() {
            log.info("!!! rocketmq producer monilog start ...");
            return new RocketMqMoniLogInterceptor.RocketMQProducerInhanceProcessor();
        }
    }

    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "monilog.web", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('web')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('web'))")
    @Bean
    FilterRegistrationBean<WebMoniLogInterceptor> webMoniLogInterceptor(MoniLogProperties moniLogProperties) {
        log.info("!!! web monilog start ...");
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
        log.info("!!! xxljob monilog start ...");
        return new XxlJobMoniLogInterceptor();
    }

    @ConditionalOnProperty(prefix = "monilog.redis", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('redis')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('redis'))")
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    @Bean
    RedisMoniLogInterceptor redisTemplateEnhancer() {
        log.info("!!! redis monilog start ...");
        return new RedisMoniLogInterceptor();
    }

    @Configuration
    @ConditionalOnProperty(prefix = "monilog.httpclient", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monilog.component.includes:*}'.equals('*') or '${monilog.component.includes}'.contains('httpclient')) and !('${monilog.component.excludes:}'.equals('*') or '${monilog.component.excludes:}'.contains('httpclient'))")
    @ConditionalOnClass(name = {"org.apache.http.client.HttpClient", "org.apache.http.impl.client.CloseableHttpClient", "org.apache.http.impl.client.HttpClientBuilder"})
    @AutoConfigureAfter(MoniLogAutoConfiguration.class)
    static class HttpClientMoniLogConfiguration {
        /**
         * 注意，只有使用Spring容器中的HttpClientBuilder对象，拦截器才会生效
         */
        @Bean
        //@ConditionalOnMissingBean(HttpClientBuilder.class)
        HttpClientBuilder httpClientBuilder() {
            return XHttpClientBuilder.create();
        }
    }
}
