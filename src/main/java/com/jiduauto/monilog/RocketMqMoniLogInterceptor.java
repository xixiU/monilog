package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.topic.TopicValidator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;

public final class RocketMqMoniLogInterceptor {
    @AllArgsConstructor
    public static class EnhancedListenerConcurrently implements MessageListenerConcurrently {
        private final MessageListenerConcurrently delegate;
        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            return new ConsumerHook<>(delegate::consumeMessage, delegate.getClass()).apply(msgs, context);
        }
    }

    @AllArgsConstructor
    public static class EnhancedListenerOrderly implements MessageListenerOrderly {
        private final MessageListenerOrderly delegate;
        @Override
        public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
            return new ConsumerHook<>(delegate::consumeMessage, delegate.getClass()).apply(msgs, context);
        }
    }

    @AllArgsConstructor
    private static class ConsumerHook<C, R> implements BiFunction<List<MessageExt>, C, R> {
        private final BiFunction<List<MessageExt>, C, R> delegate;
        private final Class<?> cls;
        @Override
        public R apply(List<MessageExt> msgs, C c) {
            MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
            // 判断开关
            if (moniLogProperties == null ||
                    !moniLogProperties.isComponentEnable(ComponentEnum.rocketmq, moniLogProperties.getRocketmq().isEnable())
                    || !moniLogProperties.isComponentEnable(ComponentEnum.rocketmq_consumer, moniLogProperties.getRocketmq().isConsumerEnable())) {
                return delegate.apply(msgs, c);
            }
            MoniLogParams params = new MoniLogParams();
            params.setServiceCls(cls);
            params.setAction("onMessage");
            params.setService(ReflectUtil.getSimpleClassName(cls));
            params.setLogPoint(LogPoint.rocketmq_consumer);
            R result;
            long start = System.currentTimeMillis();
            try {
                params.setInput(formatInputMsgs(msgs));
                MessageExt messageExt = msgs.get(0);
                String[] tags = TagBuilder.of("topic", messageExt.getTopic(), "tag", messageExt.getTags()).toArray();
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

    private static Object[] formatInputMsgs(List<MessageExt> msgs) {
        if (CollectionUtils.isEmpty(msgs)) {
            return null;
        }
        List<Object> obj = new ArrayList<>();
        try {
            for (MessageExt msg : msgs) {
                String str = new String(msg.getBody(), StandardCharsets.UTF_8);
                JSON json = StringUtil.tryConvert2Json(str);
                obj.add(json == null ? str : json);
            }
        } catch (Exception e) {
            MoniLogUtil.innerDebug("RocketMqMoniLogInterceptor.formatInputMsgs error", e.getMessage());
        }
        return obj.toArray(new Object[0]);
    }

    @Slf4j
    public static class RocketMQProducerEnhanceProcessor implements SendMessageHook {
        private static final String MONILOG_PARAMS_KEY = "__MoniLogParamsCount";
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
            // 异步的时候会调用两次sendMessageAfter,第二次的sendResult会有发送结果，取第二次
            if (CommunicationMode.ASYNC == context.getCommunicationMode() && context.getSendResult() == null) {
                return;
            }
            MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
            // 判断开关
            if (moniLogProperties == null ||
                    !moniLogProperties.isComponentEnable(ComponentEnum.rocketmq, moniLogProperties.getRocketmq().isEnable())
                    || !moniLogProperties.isComponentEnable(ComponentEnum.rocketmq_producer, moniLogProperties.getRocketmq().isProducerEnable())) {
                return;
            }
            Message message = context.getMessage();
            String topic = message.getTopic();
            // rocketmq内部消息追踪的topic,跳过
            if (TopicValidator.isSystemTopic(topic) || TopicValidator.isNotAllowedSendTopic(topic)) {
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
                logParams.setService(ReflectUtil.getSimpleClassName(logParams.getServiceCls()));
                String startTimeStr = context.getProps().get("startTime");
                long startTime = NumberUtils.isCreatable(startTimeStr) ? Long.parseLong(startTimeStr) : 0L;
                logParams.setCost(System.currentTimeMillis() - startTime);
                logParams.setException(context.getException());
                SendResult sendResult = context.getSendResult();
                SendStatus status = sendResult == null ? null : sendResult.getSendStatus();
                logParams.setOutput(sendResult);
                if (CommunicationMode.ONEWAY == context.getCommunicationMode() || sendResult == null) {
                    logParams.setSuccess(context.getException() == null);
                } else {
                    logParams.setSuccess(context.getException() == null && (status == SendStatus.SEND_OK));
                }
                if (logParams.isSuccess()) {
                    logParams.setMsgCode(ErrorEnum.SUCCESS.name());
                    logParams.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                } else {
                    ErrorInfo errorInfo = ExceptionUtil.parseException(context.getException());
                    if (errorInfo != null) {
                        logParams.setMsgCode(errorInfo.getErrorCode());
                        logParams.setMsgInfo(errorInfo.getErrorMsg());
                    } else if (sendResult != null && sendResult.getSendStatus() != null) {
                        logParams.setMsgCode(sendResult.getSendStatus().name());
                    } else {
                        logParams.setMsgCode(ErrorEnum.FAILED.name());
                    }
                    if (StringUtils.isBlank(logParams.getMsgInfo())) {
                        logParams.setMsgInfo(ErrorEnum.FAILED.getMsg());
                    }
                }
                logParams.setInput(new Object[]{getMqBody(message)});
                if (sendResult != null) {
                    Map<String, Object> sendResultMap = new HashMap<>();
                    sendResultMap.put("msgId", sendResult.getMsgId());
                    sendResultMap.put("regionId", sendResult.getRegionId() );
                    sendResultMap.put("sendStatus", sendResult.getSendStatus() );
                    logParams.setOutput(sendResultMap);
                }

                logParams.setTags(TagBuilder.of("topic", topic, "group", context.getProducerGroup(), "tag", message.getTags()).toArray());
                MoniLogUtil.log(logParams);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("sendMessageAfter error", e);
            }
        }
    }

    private static String getMqBody(Message message) {
        Charset charset = StandardCharsets.UTF_8;
        byte[] body = message.getBody();
        String mqBody = new String(body, charset);
        if (hasInvalidCharacters(body, charset)) {
            byte[] uncompress;
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
     * @param bytes   字符数组
     * @param charset 编码
     * @return 乱码返回true，否者返回false
     */
    private static boolean hasInvalidCharacters(byte[] bytes, Charset charset) {
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