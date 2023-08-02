package com.jiduauto.monitor.log;

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
@EnableConfigurationProperties(MonitorLogProperties.class)
@ConditionalOnProperty(prefix = "monitor.log", name = "enable", matchIfMissing = true)
@Slf4j
@Import({SpringUtils.class})
class MonitorLogAutoConfiguration {
    @Bean
    MonitorLogProperties monitorLogProperties(){
        return new MonitorLogProperties();
    }

    @Bean
    @ConditionalOnBean(MonitorLogPrinter.class)
    MonitorLogAop aspectProcessor() {
        return new MonitorLogAop();
    }

    @Bean
    @ConditionalOnMissingBean(MonitorLogAop.class)
    @ConditionalOnClass(MonitorLogTags.class)
    MonitorLogAop aspectUserProcessor() {
        return new MonitorLogAop();
    }

    @Order(Integer.MIN_VALUE)
    @Bean("__springUtils")
    SpringUtils springUtils() {
        return new SpringUtils();
    }

    @Bean
    @ConditionalOnMissingBean(MonitorLogPrinter.class)
    MonitorLogPrinter monitorLogPrinter() {
        log.info("!!! monitor logPrinter start ...");
        return new DefaultMonitorLogPrinter();
    }

//    @ConditionalOnProperty(prefix = "monitor.log.feign", name = "enable", havingValue = "true", matchIfMissing = true)
//    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('feign')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('feign'))")
//    @ConditionalOnClass(name = {"feign.Feign"})
//    @Bean
//    FeignMonitorInterceptor feignMonitorInterceptor() {
//        log.info("!!! feign monitor start ...");
//        return new FeignMonitorInterceptor();
//    }

    @ConditionalOnProperty(prefix = "monitor.log.grpc", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('grpc')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('grpc'))")
    @ConditionalOnClass(name = {"io.grpc.stub.AbstractStub", "io.grpc.stub.ServerCalls"})
    @Configuration
    static class GrpcMonitorLogConfiguration {
        @Order(-100)
        @GrpcGlobalServerInterceptor
        @ConditionalOnProperty(prefix = "monitor.log.grpc.server", name = "enable", havingValue = "true", matchIfMissing = true)
        GrpcMonitorLogInterceptor.GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
            log.info("!!! grpc server monitor start ...");
            return new GrpcMonitorLogInterceptor.GrpcLogPrintServerInterceptor();
        }

        @Order(-101)
        @GrpcGlobalClientInterceptor
        @ConditionalOnClass(name = "io.grpc.ClientInterceptor")
        @ConditionalOnProperty(prefix = "monitor.log.grpc.client", name = "enable", havingValue = "true", matchIfMissing = true)
        GrpcMonitorLogInterceptor.GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
            log.info("!!! grpc client monitor start ...");
            return new GrpcMonitorLogInterceptor.GrpcLogPrintClientInterceptor();
        }
    }

    @ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
    @ConditionalOnProperty(prefix = "monitor.log.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('mybatis')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('mybatis'))")
    @Bean
    MybatisMonitorLogInterceptor.MybatisInterceptor mybatisMonitorSqlFilter() {
        log.info("!!! mybatis monitor start ...");
        return new MybatisMonitorLogInterceptor.MybatisInterceptor();
    }


    @Configuration
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('rocketmq')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('rocketmq'))")
    @ConditionalOnClass(name = {"org.apache.rocketmq.client.MQAdmin"})
    static class RocketMqMonitorLogConfiguration {
        @Bean
        @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
        RocketMqMonitorLogInterceptor.RocketMQConsumerEnhanceProcessor rocketMQConsumerPostProcessor() {
            log.info("!!! rocketmq consumer monitor start ...");
            return new RocketMqMonitorLogInterceptor.RocketMQConsumerEnhanceProcessor();
        }

        @Bean
        @ConditionalOnProperty(prefix = "monitor.log.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
        RocketMqMonitorLogInterceptor.RocketMQProducerInhanceProcessor rocketMQProducerPostProcessor() {
            log.info("!!! rocketmq producer monitor start ...");
            return new RocketMqMonitorLogInterceptor.RocketMQProducerInhanceProcessor();
        }
    }

    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "monitor.log.web", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('web')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('web'))")
    @Bean
    FilterRegistrationBean<WebMonitorLogInterceptor> webMonitorLogInterceptor(MonitorLogProperties monitorLogProperties) {
        log.info("!!! web monitor start ...");
        FilterRegistrationBean<WebMonitorLogInterceptor> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(new WebMonitorLogInterceptor(monitorLogProperties));
        filterRegBean.setOrder(Integer.MAX_VALUE);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("webMonitorLogInterceptor");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }

    @ConditionalOnProperty(prefix = "monitor.log.xxljob", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('xxljob')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('xxljob'))")
    @ConditionalOnClass(name = "com.xxl.job.core.handler.IJobHandler")
    @Bean
    XxlJobLogMonitorInterceptor xxlJobLogMonitorExecuteInterceptor() {
        log.info("!!! xxljob monitor start ...");
        return new XxlJobLogMonitorInterceptor();
    }

    @ConditionalOnProperty(prefix = "monitor.log.redis", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('redis')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('redis'))")
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    @Bean
    RedisLogMonitorInterceptor redisTemplateEnhancer() {
        log.info("!!! redis monitor start ...");
        return new RedisLogMonitorInterceptor();
    }

    @Configuration
    @ConditionalOnProperty(prefix = "monitor.log.httpclient", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('httpclient')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('httpclient'))")
    @ConditionalOnClass(name = {"org.apache.http.client.HttpClient", "org.apache.http.impl.client.CloseableHttpClient", "org.apache.http.impl.client.HttpClientBuilder"})
    @AutoConfigureAfter(MonitorLogAutoConfiguration.class)
    static class HttpClientLogMonitorConfiguration {
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
