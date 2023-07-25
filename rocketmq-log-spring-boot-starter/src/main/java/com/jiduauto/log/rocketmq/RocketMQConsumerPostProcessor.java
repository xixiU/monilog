package com.jiduauto.log.rocketmq;

import com.jiduauto.log.rocketmq.hook.RocketMqConsumerHook;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class RocketMQConsumerPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DefaultRocketMQListenerContainer) {
            DefaultRocketMQListenerContainer container = (DefaultRocketMQListenerContainer) bean;
            // 进行属性填充
            container.getConsumer().getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new RocketMqConsumerHook());
        }
        return bean;
    }
}