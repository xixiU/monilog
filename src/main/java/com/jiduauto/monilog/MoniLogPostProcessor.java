package com.jiduauto.monilog;

import com.google.common.collect.Sets;
import com.xxl.job.core.handler.IJobHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * spring bean的实例化过程参考：<a href="https://blog.csdn.net/m0_37588577/article/details/127639584">...</a>
 */
@Slf4j
class MoniLogPostProcessor implements BeanPostProcessor, PriorityOrdered {
    static final Map<String, Class<?>> CACHED_CLASS = new HashMap<>();
    private static final String XXL_JOB = "com.xxl.job.core.handler.IJobHandler";
    private static final String MQ_ADMIN = "org.apache.rocketmq.client.MQAdmin";
    private static final String MQ_LISTENER_CONTAINER = "org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer";
    private final MoniLogProperties moniLogProperties;

    MoniLogPostProcessor(MoniLogProperties moniLogProperties) {
        this.moniLogProperties = moniLogProperties;
        loadClass();
        log.info(">>>MoniLogPostProcessor initializing...");
    }

    @Override
    public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
        if (!moniLogProperties.isEnable()) {
            return bean;
        }
        if (isTargetBean(bean, XXL_JOB)) {
            if (isComponentEnable("xxljob", moniLogProperties.getXxljob().isEnable())) {
                return XxlJobMoniLogInterceptor.getProxyBean((IJobHandler) bean);
            }
        }  else if (isTargetBean(bean, MQ_ADMIN) || isTargetBean(bean, MQ_LISTENER_CONTAINER) ) {
            log.info(">>>monilog rocketmq start...");
            MoniLogProperties.RocketMqProperties rocketmqProperties = moniLogProperties.getRocketmq();
            if (!rocketmqProperties.isEnable()) {
                return bean;
            }
            boolean consumerEnable = isComponentEnable("rocketmq",rocketmqProperties.isConsumerEnable());
            boolean producerEnable = isComponentEnable("rocketmq",rocketmqProperties.isProducerEnable());
            //不使用rocketmq-starter时
            if (bean instanceof DefaultMQPushConsumer && consumerEnable) {
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
            } else if (bean instanceof DefaultMQPullConsumer && consumerEnable) {
                MoniLogUtil.innerDebug("current rocketmq mode[pull] not support intercept");
            } else if (bean instanceof DefaultRocketMQListenerContainer && consumerEnable) {
                //使用了rocketmq-starter
                DefaultRocketMQListenerContainer container = (DefaultRocketMQListenerContainer) bean;
                RocketMQListener<?> bizListener = container.getRocketMQListener();
                DefaultMQPushConsumer consumer = container.getConsumer();
                MessageListener originListener = consumer.getMessageListener();
                if (originListener instanceof RocketMqMoniLogInterceptor.EnhancedListenerConcurrently || originListener instanceof RocketMqMoniLogInterceptor.EnhancedListenerOrderly) {
                    return bean;
                }
                container.setRocketMQListener(new RocketMqMoniLogInterceptor.EnhancedRocketMqListener<>(bizListener, bizListener.getClass(), consumer.getConsumerGroup()));
            } else if (bean instanceof DefaultMQProducer && producerEnable) {
                DefaultMQProducer producer = (DefaultMQProducer) bean;
                producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMqMoniLogInterceptor.RocketMQProducerEnhanceProcessor());
                return bean;
            }
        }
        return bean;
    }


    private static boolean isTargetBean(@NotNull Object bean, String className) {
        try {
            Class<?> cls = getTargetCls(className);
            return cls != null && cls.isAssignableFrom(bean.getClass());
        } catch (Exception e) {
            return false;
        }
    }

    static Class<?> getTargetCls(String className) {
        Class<?> cls = CACHED_CLASS.get(className);
        if (cls != null) {
            return cls;
        }
        try {
            return Class.forName(className);
        } catch (Exception e) {
            return null;
        }
    }


    // 校验是否排除
    private boolean isComponentEnable(String component, boolean componentEnable) {
        return moniLogProperties.isComponentEnable(component, componentEnable);
    }

    private static void loadClass() {
        Set<String> clsNames = Sets.newHashSet( XXL_JOB, MQ_ADMIN,  MQ_LISTENER_CONTAINER);
        for (String clsName : clsNames) {
            try {
                Class<?> cls = Class.forName(clsName);
                CACHED_CLASS.put(clsName, cls);
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
