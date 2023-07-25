package com.jiduauto.log.rocketmq;

import com.jiduauto.log.rocketmq.hook.RocketMQSendHook;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

public class RocketMQProducerPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DefaultMQProducer) {
            DefaultMQProducer producer = (DefaultMQProducer) bean;
            // 进行属性填充
            producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMQSendHook());
        }
        return bean;
    }
}
