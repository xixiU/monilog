package com.jiduauto.log.rocketmq;

import com.jiduauto.log.rocketmq.hook.RocketMqConsumerHook;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.autoconfigure.ListenerContainerConfiguration;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnProperty(prefix = "monitor.log.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
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