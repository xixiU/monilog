package com.jiduauto.log.rocketmqlogspringbootstart;

import com.jiduauto.log.rocketmqlogspringbootstart.aop.RocketMQConsumerInterceptor;
import com.jiduauto.log.rocketmqlogspringbootstart.interceptor.RocketMQSendInterceptor;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(DefaultMQProducer.class)
@ConditionalOnProperty(prefix = "monitor.log.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
public class RocketMQProducerInterceptorAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
    public RocketMQSendInterceptor rocketMQSendMessageHook() {
        return new RocketMQSendInterceptor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
    public RocketMQConsumerInterceptor rocketMQConsumerInterceptor() {
        return new RocketMQConsumerInterceptor();
    }
}