package com.jiduauto.log.rocketmq;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "monitor.log.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('rocketmq')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('rocketmq'))")
@ConditionalOnClass(name = {"org.apache.rocketmq.client.MQAdmin", "com.jiduauto.log.core.MonitorLogConfiguration"})
class RocketMQProducerInterceptorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
    public RocketMQConsumerInterceptor rocketMQConsumerPostProcessor() {
        return new RocketMQConsumerInterceptor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
    public RocketMQProducerInterceptor rocketMQProducerPostProcessor() {
        return new RocketMQProducerInterceptor();
    }

}