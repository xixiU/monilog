package com.jiduauto.log.rocketmq;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.jiduauto.log.core.ErrorInfo;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MQConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

@Slf4j
class RocketMQConsumerInterceptor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MQConsumer) {//不使用rocketmq-starter时
            MQConsumer consumer = (MQConsumer) bean;
            if (consumer instanceof DefaultMQPushConsumer) {
                enhanceListener((DefaultMQPushConsumer) consumer, ((DefaultMQPushConsumer) consumer).getMessageListener().getClass());
            } else if (consumer instanceof DefaultMQPullConsumer) {
                //TODO 该模型下暂不支持增强
            }
        } else if (bean instanceof DefaultRocketMQListenerContainer) {//使用了rocketmq-starter
            DefaultRocketMQListenerContainer container = (DefaultRocketMQListenerContainer) bean;
            RocketMQListener<?> bizListener = container.getRocketMQListener();
            DefaultMQPushConsumer consumer = container.getConsumer();
            MessageListener originListener = consumer.getMessageListener();
            if (originListener instanceof EnhancedListenerConcurrently || originListener instanceof EnhancedListenerOrderly) {
                return bean;
            }
            container.setRocketMQListener(new EnhancedRocketMqListener<>(bizListener, bizListener.getClass(), consumer.getConsumerGroup()));
        }
        return bean;
    }


    private void enhanceListener(DefaultMQPushConsumer consumer, Class<?> bizCls) {
        MessageListener messageListener = consumer.getMessageListener();
        String consumerGroup = consumer.getConsumerGroup();
        if (messageListener instanceof MessageListenerConcurrently) {
            consumer.setMessageListener(new EnhancedListenerConcurrently((MessageListenerConcurrently) messageListener, bizCls, consumerGroup));
        } else if (messageListener instanceof MessageListenerOrderly) {
            consumer.setMessageListener(new EnhancedListenerOrderly((MessageListenerOrderly) messageListener, bizCls, consumerGroup));
        }
    }

    @AllArgsConstructor
    static class ConsumerHook<C, R> implements BiFunction<List<MessageExt>, C, R> {
        private final BiFunction<List<MessageExt>, C, R> delegate;
        private final Class<?> cls;
        private final String consumerGroup;

        @Override
        public R apply(List<MessageExt> msgs, C c) {
            MonitorLogParams params = new MonitorLogParams();
            params.setServiceCls(cls);
            params.setAction("onMessage");
            params.setService(cls.getSimpleName());
            params.setLogPoint(LogPoint.MSG_ENTRY);
            R result;
            long start = System.currentTimeMillis();
            try {
                params.setInput(formatInputMsgs(msgs));
                MessageExt messageExt = msgs.get(0);
                String[] tags = TagBuilder.of(RocketMQLogConstant.GROUP, consumerGroup, RocketMQLogConstant.TOPIC, messageExt.getTopic(), RocketMQLogConstant.TAG, messageExt.getTags()).toArray();
                params.setTags(tags);
                result = delegate.apply(msgs, c);
                params.setSuccess(Objects.equals(result, ConsumeConcurrentlyStatus.CONSUME_SUCCESS) || Objects.equals(result, ConsumeOrderlyStatus.SUCCESS));
                params.setMsgCode(result.toString());
                params.setMsgInfo(params.isSuccess() ? "成功" : "失败");
                params.setOutput(result);
                return result;
            } catch (Throwable e) {
                params.setException(e);
                ErrorInfo errorInfo = ExceptionUtil.parseException(e);
                params.setSuccess(false);
                params.setMsgCode(errorInfo.getErrorCode());
                params.setMsgInfo(errorInfo.getErrorMsg());
                throw e;
            } finally {
                params.setCost(System.currentTimeMillis() - start);
                MonitorLogUtil.log(params);
            }
        }
    }

    @AllArgsConstructor
    static class EnhancedRocketMqListener<T> implements RocketMQListener<T> {
        private final RocketMQListener<T> delegate;
        private final Class<?> cls;
        private final String consumerGroup;

        @Override
        public void onMessage(T message) {
            MonitorLogParams params = new MonitorLogParams();
            params.setServiceCls(cls);
            params.setAction("onMessage");
            params.setService(cls.getSimpleName());
            params.setLogPoint(LogPoint.MSG_ENTRY);
            long start = System.currentTimeMillis();
            RocketMQMessageListener anno = cls.getAnnotation(RocketMQMessageListener.class);
            if (anno == null) {
                log.warn("RocketMQMessageListener is missing");
                delegate.onMessage(message);
                return;
            }
            String topic = anno.topic();
            String tag = message instanceof MessageExt ? ((MessageExt) message).getTags() : anno.selectorExpression();
            String group = anno.consumerGroup();
            try {
                params.setInput(formatInputMsg(message));
                String[] tags = TagBuilder.of(RocketMQLogConstant.GROUP, consumerGroup, RocketMQLogConstant.TOPIC, topic, RocketMQLogConstant.TAG, tag).toArray();
                params.setTags(tags);
                delegate.onMessage(message);
                params.setSuccess(true);
                params.setMsgCode(ErrorEnum.SUCCESS.name());
                params.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            } catch (Throwable e) {
                params.setException(e);
                ErrorInfo errorInfo = ExceptionUtil.parseException(e);
                params.setSuccess(false);
                params.setMsgCode(errorInfo.getErrorCode());
                params.setMsgInfo(errorInfo.getErrorMsg());
                throw e;
            } finally {
                params.setCost(System.currentTimeMillis() - start);
                MonitorLogUtil.log(params);
            }
        }
    }

    @AllArgsConstructor
    static class EnhancedListenerConcurrently implements MessageListenerConcurrently {
        private final MessageListenerConcurrently delegate;
        private final Class<?> cls;
        private final String consumerGroup;

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            return new ConsumerHook<>(delegate::consumeMessage, cls, consumerGroup).apply(msgs, context);
        }
    }

    @AllArgsConstructor
    static class EnhancedListenerOrderly implements MessageListenerOrderly {
        private final MessageListenerOrderly delegate;
        private Class<?> cls;
        private final String consumerGroup;

        @Override
        public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
            return new ConsumerHook<>(delegate::consumeMessage, cls, consumerGroup).apply(msgs, context);
        }
    }

    private static Object[] formatInputMsg(Object obj) {
        if (obj instanceof MessageExt) {
            return formatInputMsgs(Lists.newArrayList((MessageExt) obj));
        }
        if (obj.getClass().isArray()) {
            return (Object[]) obj;
        }
        return new Object[]{obj};
    }

    private static Object[] formatInputMsgs(List<MessageExt> msgs) {
        if (CollectionUtils.isEmpty(msgs)) {
            return null;
        }
        List<Object> obj = new ArrayList<>();
        for (MessageExt msg : msgs) {
            String str = new String(msg.getBody(), StandardCharsets.UTF_8);
            JSON json = StringUtil.tryConvert2Json(str);
            obj.add(json == null ? str : json);
        }
        return obj.toArray(new Object[0]);
    }
}