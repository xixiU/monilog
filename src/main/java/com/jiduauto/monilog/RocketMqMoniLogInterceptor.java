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
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

class RocketMqMoniLogInterceptor {
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
            MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
            // 判断开关
            if (moniLogProperties == null ||
                    !moniLogProperties.isComponentEnable("rocketmq", moniLogProperties.getRocketmq().isEnable())
            || !moniLogProperties.isComponentEnable("rocketmq-consumer", moniLogProperties.getRocketmq().isConsumerEnable())) {
                return delegate.apply(msgs, c);
            }
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
            MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
            // 判断开关
            if (moniLogProperties == null ||
                    !moniLogProperties.isComponentEnable("rocketmq", moniLogProperties.getRocketmq().isEnable())
                            || !moniLogProperties.isComponentEnable("rocketmq-consumer", moniLogProperties.getRocketmq().isConsumerEnable())) {
                delegate.onMessage(message);
                return;
            }
            MoniLogParams params = new MoniLogParams();
            params.setServiceCls(cls);
            params.setAction("onMessage");
            params.setService(cls.getSimpleName());
            params.setLogPoint(LogPoint.rocketmq_consumer);
            long start = System.currentTimeMillis();
            RocketMQMessageListener anno = cls.getAnnotation(RocketMQMessageListener.class);
            if (anno == null) {
                MoniLogUtil.innerDebug("@RocketMQMessageListener is missing");
                delegate.onMessage(message);
                return;
            }
            String topic = message instanceof MessageExt ? ((MessageExt) message).getTopic() : anno.topic();

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

    @Slf4j
    static class RocketMQProducerEnhanceProcessor implements SendMessageHook {
        @Override
        public String hookName() {
            return this.getClass().getName();
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
            MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
            // 判断开关
            if (moniLogProperties == null ||
                    !moniLogProperties.isComponentEnable("rocketmq", moniLogProperties.getRocketmq().isEnable())
                            || !moniLogProperties.isComponentEnable("rocketmq-producer", moniLogProperties.getRocketmq().isProducerEnable())) {
                return;
            }
            // 在发送完成后拦截，计算耗时并打印监控信息
            StackTraceElement st = ThreadUtil.getNextClassFromStack(DefaultMQProducerImpl.class);
            String clsName;
            String action;
            if (st == null) {
                clsName = MQProducer.class.getCanonicalName();
                action = "send";
            } else {
                clsName = st.getClassName();
                action = st.getMethodName();
            }
            MoniLogParams logParams = new MoniLogParams();
            logParams.setLogPoint(LogPoint.rocketmq_producer);
            logParams.setAction(action);
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
                logParams.setMsgCode(logParams.isSuccess() ? ErrorEnum.SUCCESS.name() : status == null ? ErrorEnum.SUCCESS.getMsg() : status.name());
                logParams.setMsgInfo(logParams.isSuccess() ? ErrorEnum.SUCCESS.getMsg() : ErrorEnum.FAILED.getMsg());
                Message message = context.getMessage();
                logParams.setInput(new Object[]{getMqBody(message)});
                logParams.setTags(TagBuilder.of("topic", message.getTopic(), "group", context.getProducerGroup(), "tag", message.getTags()).toArray());
                MoniLogUtil.log(logParams);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("sendMessageAfter error", e);
            }
        }
    }

    private static String getMqBody(Message message){
        Charset charset = StandardCharsets.UTF_8;
        byte[] body = message.getBody();
        String mqBody = new String(body, charset);
        if (hasInvalidCharacters(body,charset)) {
            byte[] uncompress ;
            try {
                uncompress = UtilAll.uncompress(body);
            } catch (IOException e) {
                return mqBody;
            }
            return new String(uncompress, charset);
        }
        return mqBody;

    }


    /**
     * 获取消息体
     * org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl#tryToCompressMessage(org.apache.rocketmq.common.message.Message)
     * 大于4096字节的字符串会压缩。由于此标记没有记录在content中，通过判断字符串是否乱码进行解压缩。
     *
     * @param bytes 字符数组
     * @param charset 编码
     * @return 乱码返回true，否者返回false
     */
    public static boolean hasInvalidCharacters(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return false; // 字符串没有乱码
        } catch (Exception e) {
            return true; // 字符串有乱码
        }
    }

}