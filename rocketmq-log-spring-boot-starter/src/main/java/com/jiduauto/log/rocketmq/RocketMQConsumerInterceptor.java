package com.jiduauto.log.rocketmq;

import com.alibaba.fastjson.JSON;
import com.jiduauto.log.core.ErrorInfo;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.ExceptionUtil;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.jiduauto.log.core.util.StringUtil;
import com.jiduauto.log.core.util.TagBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
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
        if (bean instanceof DefaultRocketMQListenerContainer) {
            DefaultRocketMQListenerContainer container = (DefaultRocketMQListenerContainer) bean;
            Class<? extends RocketMQListener> cls = container.getRocketMQListener().getClass();
            DefaultMQPushConsumer consumer = container.getConsumer();
            MessageListener messageListener = consumer.getMessageListener();
            String consumerGroup = consumer.getConsumerGroup();
            if (messageListener instanceof MessageListenerConcurrently) {
                consumer.setMessageListener(new EnhancedListenerConcurrently((MessageListenerConcurrently) messageListener, cls, consumerGroup));
            } else if (messageListener instanceof MessageListenerOrderly) {
                consumer.setMessageListener(new EnhancedListenerOrderly((MessageListenerOrderly) messageListener, cls, consumerGroup));
            }
        }
        return bean;
    }

    @AllArgsConstructor
    static class ConsumerHook<C, R> implements BiFunction<List<MessageExt>, C, R> {
        private final BiFunction<List<MessageExt>, C, R> delegate;
        private final Class<? extends RocketMQListener> cls;
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
    static class EnhancedListenerConcurrently implements MessageListenerConcurrently {
        private final MessageListenerConcurrently delegate;
        private final Class<? extends RocketMQListener> cls;
        private final String consumerGroup;

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            return new ConsumerHook<>(delegate::consumeMessage, cls, consumerGroup).apply(msgs, context);
        }
    }

    @AllArgsConstructor
    static class EnhancedListenerOrderly implements MessageListenerOrderly {
        private final MessageListenerOrderly delegate;
        private Class<? extends RocketMQListener> cls;
        private final String consumerGroup;

        @Override
        public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
            return new ConsumerHook<>(delegate::consumeMessage, cls, consumerGroup).apply(msgs, context);
        }
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