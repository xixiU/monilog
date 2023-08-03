package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

class RocketMqMoniLogInterceptor {
    @Slf4j
    static class RocketMQConsumerEnhanceProcessor  {
//        @Override
//        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//            if (bean instanceof MQConsumer) {//不使用rocketmq-starter时
//                if (bean instanceof DefaultMQPushConsumer) {
//                    DefaultMQPushConsumer consumer = (DefaultMQPushConsumer) bean;
//                    Class<?> bizCls = consumer.getMessageListener().getClass();
//                    MessageListener messageListener = consumer.getMessageListener();
//                    String consumerGroup = consumer.getConsumerGroup();
//                    if (messageListener instanceof MessageListenerConcurrently) {
//                        consumer.setMessageListener(new EnhancedListenerConcurrently((MessageListenerConcurrently) messageListener, bizCls, consumerGroup));
//                    } else if (messageListener instanceof MessageListenerOrderly) {
//                        consumer.setMessageListener(new EnhancedListenerOrderly((MessageListenerOrderly) messageListener, bizCls, consumerGroup));
//                    }
//                } else if (bean instanceof DefaultMQPullConsumer) {
//                    MoniLogUtil.log("current rocketmq mode[pull] not support intercept");
//                }
//            } else if (bean instanceof DefaultRocketMQListenerContainer) {//使用了rocketmq-starter
//                DefaultRocketMQListenerContainer container = (DefaultRocketMQListenerContainer) bean;
//                RocketMQListener<?> bizListener = container.getRocketMQListener();
//                DefaultMQPushConsumer consumer = container.getConsumer();
//                MessageListener originListener = consumer.getMessageListener();
//                if (originListener instanceof EnhancedListenerConcurrently || originListener instanceof EnhancedListenerOrderly) {
//                    return bean;
//                }
//                container.setRocketMQListener(new EnhancedRocketMqListener<>(bizListener, bizListener.getClass(), consumer.getConsumerGroup()));
//            }
//            return bean;
//        }

        @AllArgsConstructor
        protected static class EnhancedListenerConcurrently implements MessageListenerConcurrently {
            private final MessageListenerConcurrently delegate;
            private final Class<?> cls;
            private final String consumerGroup;

            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                return new ConsumerHook<>(delegate::consumeMessage, cls, consumerGroup).apply(msgs, context);
            }
        }

        @AllArgsConstructor
        protected static class EnhancedListenerOrderly implements MessageListenerOrderly {
            private final MessageListenerOrderly delegate;
            private Class<?> cls;
            private final String consumerGroup;

            @Override
            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
                return new ConsumerHook<>(delegate::consumeMessage, cls, consumerGroup).apply(msgs, context);
            }
        }

        @AllArgsConstructor
        private static class ConsumerHook<C, R> implements BiFunction<List<MessageExt>, C, R> {
            private final BiFunction<List<MessageExt>, C, R> delegate;
            private final Class<?> cls;
            private final String consumerGroup;

            @Override
            public R apply(List<MessageExt> msgs, C c) {
                MoniLogParams params = new MoniLogParams();
                params.setServiceCls(cls);
                params.setAction("onMessage");
                params.setService(cls.getSimpleName());
                params.setLogPoint(LogPoint.rocketmq_consumer);
                R result;
                long start = System.currentTimeMillis();
                try {
                    params.setInput(formatInputMsgs(msgs));
                    MessageExt messageExt = msgs.get(0);
                    String[] tags = TagBuilder.of("group", consumerGroup, "topic", messageExt.getTopic(), "tag", messageExt.getTags()).toArray();
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
                    MoniLogUtil.log(params);
                }
            }
        }

        @AllArgsConstructor
        protected static class EnhancedRocketMqListener<T> implements RocketMQListener<T> {
            private final RocketMQListener<T> delegate;
            private final Class<?> cls;
            private final String consumerGroup;

            @Override
            public void onMessage(T message) {
                MoniLogParams params = new MoniLogParams();
                params.setServiceCls(cls);
                params.setAction("onMessage");
                params.setService(cls.getSimpleName());
                params.setLogPoint(LogPoint.rocketmq_consumer);
                long start = System.currentTimeMillis();
                RocketMQMessageListener anno = cls.getAnnotation(RocketMQMessageListener.class);
                if (anno == null) {
                    MoniLogUtil.log("RocketMQMessageListener is missing");
                    delegate.onMessage(message);
                    return;
                }
                String topic = message instanceof MessageExt ? ((MessageExt) message).getTopic() : anno.topic();;

                if (topic.startsWith("${")) {
                    topic = SpringUtils.getApplicationContext().getEnvironment().resolvePlaceholders(topic);
                }
                String tag = message instanceof MessageExt ? ((MessageExt) message).getTags() : anno.selectorExpression();

                try {
                    params.setInput(formatInputMsg(message));
                    String[] tags = TagBuilder.of("group", consumerGroup, "topic", topic, "tag", tag).toArray();
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
                    MoniLogUtil.log(params);
                }
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


    @Slf4j
    static class RocketMQProducerInhanceProcessor {
//        @Override
//        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//            if (bean instanceof DefaultMQProducer) {
//                DefaultMQProducer producer = (DefaultMQProducer) bean;
//                producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMQSendHook());
//            }
//            return bean;
//        }

        protected static class RocketMQSendHook implements SendMessageHook {
            @Override
            public String hookName() {
                return RocketMQSendHook.class.getName();
            }

            @Override
            public void sendMessageBefore(SendMessageContext context) {
                if (null == context.getProps()) {
                    context.setProps(new HashMap<>());
                }
                context.getProps().put("startTime", String.valueOf(System.currentTimeMillis()));
            }

            @Override
            public void sendMessageAfter(SendMessageContext context) {
                // 在发送完成后拦截，计算耗时并打印监控信息
                StackTraceElement st = ThreadUtil.getNextClassFromStack(DefaultMQProducerImpl.class, "org.apache.rocketmq", "org.springframework");
                String clsName = st.getClassName();
                MoniLogParams logParams = new MoniLogParams();
                logParams.setLogPoint(LogPoint.rocketmq_producer);
                logParams.setAction(st.getMethodName());
                try {
                    logParams.setServiceCls(Class.forName(clsName));
                    logParams.setService(logParams.getServiceCls().getSimpleName());
                    String startTimeStr = context.getProps().get("startTime");
                    long startTime = NumberUtils.isCreatable(startTimeStr) ? Long.parseLong(startTimeStr) : 0L;
                    logParams.setCost(System.currentTimeMillis() - startTime);
                    logParams.setException(context.getException());
                    SendResult sendResult = context.getSendResult();
                    SendStatus status = sendResult == null ? null : sendResult.getSendStatus();
                    logParams.setOutput(sendResult);
                    logParams.setSuccess(context.getException() == null && status == SendStatus.SEND_OK);
                    logParams.setMsgCode(logParams.isSuccess() ? ErrorEnum.FAILED.name() : status.name());
                    logParams.setMsgInfo(logParams.isSuccess() ? ErrorEnum.FAILED.getMsg() : ErrorEnum.SUCCESS.getMsg());
                    Message message = context.getMessage();
                    logParams.setInput(new Object[]{new String(message.getBody(), StandardCharsets.UTF_8)});
                    logParams.setTags(TagBuilder.of("topic", message.getTopic(), "group", context.getProducerGroup(), "tag", message.getTags()).toArray());
                    MoniLogUtil.log(logParams);
                } catch (Exception e) {
                    MoniLogUtil.log("sendMessageAfter error: {}", e.getMessage());
                }
            }
        }
    }
}