package com.jiduauto.monitor.log;

import com.xxl.job.core.handler.IJobHandler;
import feign.Feign;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.annotation.Resource;

/**
 * @description: 启动类
 * @author rongjie.yuan
 * @date 2023/7/28 17:06
 */
@Configuration
@EnableConfigurationProperties(MonitorLogProperties.class)
@ConditionalOnProperty(prefix = "monitor.log", name = "enable", matchIfMissing = true)
@Slf4j
class MonitorLogAutoConfiguration {
    @Resource
    private MonitorLogProperties monitorLogProperties;

    @Bean
    MonitorLogAop aspectProcessor() {
        return new MonitorLogAop();
    }

    @Bean
    @ConditionalOnMissingBean(MonitorLogPrinter.class)
    MonitorLogPrinter monitorLogPrinter() {
        log.info("!!! core monitor start ……");
        return new DefaultMonitorLogPrinter(monitorLogProperties.getPrinter());
    }

    @ConditionalOnProperty(prefix = "monitor.log.feign", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('feign')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('feign'))")
    @ConditionalOnClass({Feign.class})
    @Bean
    FeignMonitorInterceptor.FeignClientEnhanceProcessor feignClientEnhanceProcessor() {
        log.info("!!! feign monitor start ……");
        return new FeignMonitorInterceptor.FeignClientEnhanceProcessor(monitorLogProperties.getFeign());
    }

    @ConditionalOnProperty(prefix = "monitor.log.grpc", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('grpc')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('grpc'))")
    @ConditionalOnClass(name = {"io.grpc.stub.AbstractStub", "io.grpc.stub.ServerCalls", "com.jiduauto.monitor.log.CoreMonitorLogConfiguration"})
    @Configuration
    class GrpcMonitorLogConfiguration {
        @Order(-100)
        @GrpcGlobalServerInterceptor
        @ConditionalOnProperty(prefix = "monitor.log.grpc.server", name = "enable", havingValue = "true", matchIfMissing = true)
        GrpcMonitorLogInterceptor.GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
            log.info("!!! grpc server monitor start ……");
            return new GrpcMonitorLogInterceptor.GrpcLogPrintServerInterceptor();
        }

        @Order(-101)
        @GrpcGlobalClientInterceptor
        @ConditionalOnClass(name = "io.grpc.ClientInterceptor")
        @ConditionalOnProperty(prefix = "monitor.log.grpc.client", name = "enable", havingValue = "true", matchIfMissing = true)
        GrpcMonitorLogInterceptor.GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
            log.info("!!! grpc client monitor start ……");
            return new GrpcMonitorLogInterceptor.GrpcLogPrintClientInterceptor();
        }
    }

    @ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
    @ConditionalOnProperty(prefix = "monitor.log.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('mybatis')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('mybatis'))")
    @Bean
    MybatisMonitorLogInterceptor.MybatisInterceptor mybatisMonitorSqlFilter() {
        log.info("!!! mybatis monitor start ……");
        return new MybatisMonitorLogInterceptor.MybatisInterceptor(monitorLogProperties.getMybatis());
    }


    @Configuration
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('rocketmq')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('rocketmq'))")
    @ConditionalOnClass(name = {"org.apache.rocketmq.client.MQAdmin", "com.jiduauto.monitor.log.CoreMonitorLogConfiguration"})
    class RocketMqMonitorLogConfiguration{
        @Bean
        @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
        RocketMqMonitorLogInterceptor.RocketMQConsumerInterceptor rocketMQConsumerPostProcessor() {
            log.info("!!! rocketmq consumer monitor start ……");
            return new RocketMqMonitorLogInterceptor.RocketMQConsumerInterceptor();
        }

        @Bean
        @ConditionalOnProperty(prefix = "monitor.log.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
        RocketMqMonitorLogInterceptor.RocketMQProducerInterceptor rocketMQProducerPostProcessor() {
            log.info("!!! rocketmq producer monitor start ……");
            return new RocketMqMonitorLogInterceptor.RocketMQProducerInterceptor();
        }
    }

    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "monitor.log.web", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('web')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('web'))")
    @Bean
    FilterRegistrationBean<WebMonitorLogFilter> logMonitorFilterBean() {
        log.info("!!! web monitor start ……");
        FilterRegistrationBean<WebMonitorLogFilter> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(new WebMonitorLogFilter());
        filterRegBean.setOrder(Integer.MAX_VALUE);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("logMonitorFilter");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }

    @ConditionalOnProperty(prefix = "monitor.log.xxljob", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('xxljob')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('xxljob'))")
    @ConditionalOnClass({IJobHandler.class})
    @Bean
    XxlJobLogMonitorExecuteInterceptor xxlJobLogMonitorExecuteInterceptor() {
        log.info("!!! xxljob monitor start ……");
        return new XxlJobLogMonitorExecuteInterceptor();
    }
}
