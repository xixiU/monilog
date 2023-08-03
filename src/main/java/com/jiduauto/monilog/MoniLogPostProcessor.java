package com.jiduauto.monilog;

import com.xxl.job.core.handler.IJobHandler;
import feign.Client;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListener;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Set;

@Slf4j
public class MoniLogPostProcessor implements BeanPostProcessor, PriorityOrdered {
    private static final String FEIGN_CLIENT = "feign.Client";
    private static final String XXL_JOB = "com.xxl.job.core.handler.IJobHandler";
    private static final String REDIS_CONNECTION = "org.springframework.data.redis.connection.RedisConnectionFactory";
    private static final String REDIS_TEMPLATE = "org.springframework.data.redis.core.RedisTemplate";
    private static final String MQ_ADMIN = "org.apache.rocketmq.client.MQAdmin";
    private static final String MQ_LISTENER_CONTAINER = "org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer";
    private static final String MQ_PUSH_CONSUMER = "org.apache.rocketmq.client.consumer.DefaultMQPushConsumer";
    private static final String MQ_PULL_CONSUMER = "org.apache.rocketmq.client.consumer.DefaultMQPullConsumer";
    private static final String MQ_PRODUCER = "org.apache.rocketmq.client.producer.DefaultMQProducer";
    private final MoniLogProperties moniLogProperties;

    public MoniLogPostProcessor(MoniLogProperties moniLogProperties) {
        this.moniLogProperties = moniLogProperties;
    }

    @Override
    public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
        if (!moniLogProperties.isEnable()) {
            return bean;
        }
        if (checkBeanExist(bean, "feign.Client")) {
            if (isComponentEnable("feign", moniLogProperties.getFeign().isEnable())) {
                log.info(">>>monilog feign start...");
                return FeignMoniLogInterceptor.getProxyBean((Client) bean);
            }
        } else if (checkBeanExist(bean, "com.xxl.job.core.handler.IJobHandler")) {
            if (isComponentEnable("xxljob", moniLogProperties.getXxljob().isEnable())) {
                return XxlJobMoniLogInterceptor.getProxyBean((IJobHandler) bean);
            }
        } else if (checkBeanExist(bean, "org.springframework.data.redis.connection.RedisConnectionFactory") || checkBeanExist(bean, "org.springframework.data.redis.core.RedisTemplate")) {

            if (isComponentEnable("redis", moniLogProperties.getRedis().isEnable())) {
                if (checkBeanExist(bean, "org.springframework.data.redis.connection.RedisConnectionFactory")) {
                    log.info(">>>monilog redis start...");
                    return RedisMoniLogInterceptor.getProxyBean(bean);
                } else {
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
        } else if (checkBeanExist(bean, "org.apache.rocketmq.client.MQAdmin")
                || checkBeanExist(bean, "org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer")) {
            log.info(">>>monilog recoketmq start...");
            MoniLogProperties.RocketMqProperties rocketmqProperties = moniLogProperties.getRocketmq();
            if (!rocketmqProperties.isEnable()) {
                return bean;
            }
            boolean consumerEnable = rocketmqProperties.isConsumerEnable();
            boolean producerEnable = rocketmqProperties.isProducerEnable();
            //不使用rocketmq-starter时
            if (checkBeanExist(bean, "org.apache.rocketmq.client.consumer.DefaultMQPushConsumer") && consumerEnable) {
                DefaultMQPushConsumer consumer = (DefaultMQPushConsumer) bean;
                Class<?> bizCls = consumer.getMessageListener().getClass();
                MessageListener messageListener = consumer.getMessageListener();
                String consumerGroup = consumer.getConsumerGroup();
                if (messageListener instanceof MessageListenerConcurrently) {
                    consumer.setMessageListener(new RocketMqMoniLogInterceptor.EnhancedListenerConcurrently((MessageListenerConcurrently) messageListener, bizCls, consumerGroup));
                } else if (messageListener instanceof MessageListenerOrderly) {
                    consumer.setMessageListener(new RocketMqMoniLogInterceptor.EnhancedListenerOrderly((MessageListenerOrderly) messageListener, bizCls, consumerGroup));
                }
                return bean;
            } else if (checkBeanExist(bean, "org.apache.rocketmq.client.consumer.DefaultMQPullConsumer") && consumerEnable) {
                MoniLogUtil.innerDebug("current rocketmq mode[pull] not support intercept");
            } else if (checkBeanExist(bean, "org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer") && consumerEnable) {
                //使用了rocketmq-starter
                DefaultRocketMQListenerContainer container = (DefaultRocketMQListenerContainer) bean;
                RocketMQListener<?> bizListener = container.getRocketMQListener();
                DefaultMQPushConsumer consumer = container.getConsumer();
                MessageListener originListener = consumer.getMessageListener();
                if (checkBeanExist(originListener, "com.jiduauto.monilog.RocketMqMoniLogInterceptor.EnhancedListenerConcurrently") ||
                        checkBeanExist(originListener, "com.jiduauto.monilog.RocketMqMoniLogInterceptor.EnhancedListenerOrderly")) {
                    return bean;
                }
                container.setRocketMQListener(new RocketMqMoniLogInterceptor.EnhancedRocketMqListener<>(bizListener, bizListener.getClass(), consumer.getConsumerGroup()));
            } else if (checkBeanExist(bean, "org.apache.rocketmq.client.producer.DefaultMQProducer") && producerEnable) {
                DefaultMQProducer producer = (DefaultMQProducer) bean;
                producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMqMoniLogInterceptor.RocketMQProducerEnhanceProcessor());
                return bean;
            }
        }

        return bean;
    }


    private boolean checkBeanExist(@NotNull Object bean, String className) {
        try {
            Class<?> aClass = Class.forName(className);
            return aClass.isAssignableFrom(bean.getClass());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }


    // 校验是否排除
    private boolean isComponentEnable(String component, Boolean componentEnable) {
        if (!Boolean.TRUE.equals(componentEnable)) {
            return false;
        }
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
