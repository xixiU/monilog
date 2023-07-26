package com.jiduauto.log.rocketmq;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "monitor.log.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('rocketmq')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('rocketmq'))")
@ConditionalOnClass(name = "org.apache.rocketmq.client.MQAdmin")
public class RocketMQProducerInterceptorAutoConfiguration {
    // aop的注解是可以使用的，但是会对业务有要求，先注释掉
//    @Bean
//    @ConditionalOnBean(RocketMQTemplate.class)
//    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
//    public RocketMqProducerAop rocketMQProducerAop() {
//        return new RocketMqProducerAop();
//    }
//
//    @Bean
//    @ConditionalOnBean(RocketMQListener.class)
//    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
//    public RocketMQConsumerAop rocketMQConsumerAop() {
//        return new RocketMQConsumerAop();
//    }

    //
    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
    public RocketMQConsumerPostProcessor rocketMQConsumerPostProcessor() {
        return new RocketMQConsumerPostProcessor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
    public RocketMQProducerPostProcessor rocketMQProducerPostProcessor() {
        return new RocketMQProducerPostProcessor();
    }

}