package com.jiduauto.log.rocketmq;

import com.jiduauto.log.rocketmq.interceptor.RocketMQSendHook;
import com.jiduauto.log.rocketmq.interceptor.RocketMqConsumerHook;
import com.jiduauto.log.core.util.SpringUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.autoconfigure.ListenerContainerConfiguration;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.annotation.PostConstruct;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "monitor.log.rocketmq", name = "enable", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(RocketMQAutoConfiguration.class)
@Import(ListenerContainerConfiguration.class)
public class RocketMQProducerInterceptorAutoConfiguration {
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


    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.producer", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DefaultMQProducer.class})
    public DefaultMQProducer registerProducerHook(DefaultMQProducer defaultMQProducer) {
        defaultMQProducer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMQSendHook());
        return defaultMQProducer;
    }

    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DefaultMQPushConsumer.class})
    public DefaultMQPushConsumer registerProducerHook(DefaultMQPushConsumer defaultMQPushConsumer) {
        defaultMQPushConsumer.getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new RocketMqConsumerHook());
        return defaultMQPushConsumer;
    }


    @Bean
    @ConditionalOnProperty(prefix = "monitor.log.rocketmq.consumer", name = "enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({RocketMQMessageListener.class})
    public DefaultRocketMQListenerContainer registerProducerHook(DefaultRocketMQListenerContainer defaultRocketMQListenerContainer) {
        defaultRocketMQListenerContainer.getConsumer().getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new RocketMqConsumerHook());
        return defaultRocketMQListenerContainer;
    }

    @PostConstruct
    public void registerProducerHook(){
        Map<String, DefaultRocketMQListenerContainer> beansOfTypeMap = SpringUtils.getBeansOfType(DefaultRocketMQListenerContainer.class);
        if (MapUtils.isEmpty(beansOfTypeMap)) {
           return;
        }
        for (Map.Entry<String, DefaultRocketMQListenerContainer> containerEntry : beansOfTypeMap.entrySet()) {
            DefaultRocketMQListenerContainer value = containerEntry.getValue();
            value.getConsumer().getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new RocketMqConsumerHook());
        }
    }


}