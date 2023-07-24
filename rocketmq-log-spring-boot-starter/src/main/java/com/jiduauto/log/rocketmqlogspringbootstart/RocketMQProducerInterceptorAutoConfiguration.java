package com.jiduauto.log.rocketmqlogspringbootstart;

import com.jiduauto.log.rocketmqlogspringbootstart.aop.RocketMQConsumerAop;
import com.jiduauto.log.rocketmqlogspringbootstart.aop.RocketMqProducerAop;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "monitor.log.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
public class RocketMQProducerInterceptorAutoConfiguration {
    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
    public RocketMqProducerAop rocketMQProducerAop() {
        return new RocketMqProducerAop();
    }

    @Bean
    @ConditionalOnBean(RocketMQListener.class)
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
    public RocketMQConsumerAop rocketMQConsumerAop() {
        return new RocketMQConsumerAop();
    }
}