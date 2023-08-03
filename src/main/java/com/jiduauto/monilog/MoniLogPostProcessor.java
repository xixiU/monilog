package com.jiduauto.monilog;

import feign.Client;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MQConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListener;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Set;


public class MoniLogPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private MoniLogProperties moniLogProperties;

    public MoniLogPostProcessor(MoniLogProperties moniLogProperties) {
        this.moniLogProperties = moniLogProperties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!moniLogProperties.isEnable()) {
            return bean;
        }
        // feign
        if (needComponent("feign", moniLogProperties.getFeign().isEnable()) && bean instanceof Client) {
            return FeignMoniLogInterceptor.getProxyBean(bean);
        }
        // redis
        if (needComponent("redis", moniLogProperties.getRedis().isEnable())) {
            if (bean instanceof RedisConnectionFactory || beanName.equals("redisConnectionFactory")) {
                return RedisMoniLogInterceptor.getProxyBean(bean);
            } else if (bean instanceof RedisTemplate) {
                RedisSerializer<?> defaultSerializer = ((RedisTemplate<?, ?>) bean).getDefaultSerializer();
                RedisSerializer<?> keySerializer = ((RedisTemplate<?, ?>) bean).getKeySerializer();
                RedisSerializer<?> valueSerializer = ((RedisTemplate<?, ?>) bean).getValueSerializer();
                RedisSerializer<String> stringSerializer = ((RedisTemplate<?, ?>) bean).getStringSerializer();
                RedisSerializer<?> hashKeySerializer = ((RedisTemplate<?, ?>) bean).getHashKeySerializer();
                RedisSerializer<?> hashValueSerializer = ((RedisTemplate<?, ?>) bean).getHashValueSerializer();
                //...
                System.out.println("...redistemplate...");
                return bean;
            }
        }
        // rocketmq
        MoniLogProperties.RocketMqProperties rocketmqProperties = moniLogProperties.getRocketmq();
        if (needComponent("rocketmq", rocketmqProperties.isEnable())) {
            if (rocketmqProperties.isConsumerEnable() && bean instanceof MQConsumer) {
                //不使用rocketmq-starter时
                if (bean instanceof DefaultMQPushConsumer) {
                    DefaultMQPushConsumer consumer = (DefaultMQPushConsumer) bean;
                    Class<?> bizCls = consumer.getMessageListener().getClass();
                    MessageListener messageListener = consumer.getMessageListener();
                    String consumerGroup = consumer.getConsumerGroup();
                    if (messageListener instanceof MessageListenerConcurrently) {
                        consumer.setMessageListener(new RocketMqMoniLogInterceptor.RocketMQConsumerEnhanceProcessor.EnhancedListenerConcurrently((MessageListenerConcurrently) messageListener, bizCls, consumerGroup));
                    } else if (messageListener instanceof MessageListenerOrderly) {
                        consumer.setMessageListener(new RocketMqMoniLogInterceptor.RocketMQConsumerEnhanceProcessor.EnhancedListenerOrderly((MessageListenerOrderly) messageListener, bizCls, consumerGroup));
                    }
                    return bean;
                } else if (bean instanceof DefaultMQPullConsumer) {
                    MoniLogUtil.log("current rocketmq mode[pull] not support intercept");
                }
            } else if (rocketmqProperties.isConsumerEnable() && bean instanceof DefaultRocketMQListenerContainer) {
                //使用了rocketmq-starter
                DefaultRocketMQListenerContainer container = (DefaultRocketMQListenerContainer) bean;
                RocketMQListener<?> bizListener = container.getRocketMQListener();
                DefaultMQPushConsumer consumer = container.getConsumer();
                MessageListener originListener = consumer.getMessageListener();
                if (originListener instanceof RocketMqMoniLogInterceptor.RocketMQConsumerEnhanceProcessor.EnhancedListenerConcurrently || originListener instanceof RocketMqMoniLogInterceptor.RocketMQConsumerEnhanceProcessor.EnhancedListenerOrderly) {
                    return bean;
                }
                container.setRocketMQListener(new RocketMqMoniLogInterceptor.RocketMQConsumerEnhanceProcessor.EnhancedRocketMqListener<>(bizListener, bizListener.getClass(), consumer.getConsumerGroup()));
                return bean;
            }
            if (rocketmqProperties.isProducerEnable() && bean instanceof DefaultMQProducer) {
                DefaultMQProducer producer = (DefaultMQProducer) bean;
                producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMqMoniLogInterceptor.RocketMQProducerInhanceProcessor.RocketMQSendHook());
                return bean;
            }
        }

        return bean;

    }

    @Override
    public int getOrder() {
        return  Ordered.HIGHEST_PRECEDENCE;
    }

    // 校验是否排除
    private boolean needComponent(String component, Boolean componentEnable){
        if (!Boolean.TRUE.equals(componentEnable)) {
            return false;
        }
//        if (moniLogProperties == null) {
//            moniLogProperties = SpringUtils.getBean(MoniLogProperties.class);
//        }
        Set<String> componentIncludes = moniLogProperties.getComponentIncludes();
        if (CollectionUtils.isEmpty(componentIncludes)) {
            return false;
        }
        if (componentIncludes.contains("*") || componentIncludes.contains(component)) {
            Set<String> componentExcludes = moniLogProperties.getComponentExcludes();
            if (CollectionUtils.isEmpty(componentExcludes)) {
                return true;
            }
            if (componentExcludes.contains("*") || componentExcludes.contains(component)) {
                return false;
            }
            return true;
        }
        return false;
    }
}
