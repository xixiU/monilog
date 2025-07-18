package com.example.monilog;

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class RocketMqMoniLogInterceptor {
    // Will be created at broker when isAutoCreateTopicEnable
    private static final String AUTO_CREATE_TOPIC_KEY_TOPIC = "TBW102";
    private static final String RMQ_SYS_SCHEDULE_TOPIC = "SCHEDULE_TOPIC_XXXX";
    private static final String RMQ_SYS_BENCHMARK_TOPIC = "BenchmarkTest";
    private static final String RMQ_SYS_TRANS_HALF_TOPIC = "RMQ_SYS_TRANS_HALF_TOPIC";
    private static final String RMQ_SYS_TRACE_TOPIC = "RMQ_SYS_TRACE_TOPIC";
    private static final String RMQ_SYS_TRANS_OP_HALF_TOPIC = "RMQ_SYS_TRANS_OP_HALF_TOPIC";
    private static final String RMQ_SYS_TRANS_CHECK_MAX_TIME_TOPIC = "TRANS_CHECK_MAX_TIME_TOPIC";
    private static final String RMQ_SYS_SELF_TEST_TOPIC = "SELF_TEST_TOPIC";
    private static final String RMQ_SYS_OFFSET_MOVED_EVENT = "OFFSET_MOVED_EVENT";
    public static final String SYSTEM_TOPIC_PREFIX = "rmq_sys_";
    private static final Set<String> SYSTEM_TOPIC_SET = new HashSet<>(10);
    /**
     * Topics'set which client can not send msg!
     */
    private static final Set<String> NOT_ALLOWED_SEND_TOPIC_SET = new HashSet<>(8);

    static {
        SYSTEM_TOPIC_SET.add(AUTO_CREATE_TOPIC_KEY_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_SCHEDULE_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_BENCHMARK_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_TRANS_HALF_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_TRACE_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_TRANS_OP_HALF_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_TRANS_CHECK_MAX_TIME_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_SELF_TEST_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_OFFSET_MOVED_EVENT);

        NOT_ALLOWED_SEND_TOPIC_SET.add(RMQ_SYS_SCHEDULE_TOPIC);
        NOT_ALLOWED_SEND_TOPIC_SET.add(RMQ_SYS_TRANS_HALF_TOPIC);
        NOT_ALLOWED_SEND_TOPIC_SET.add(RMQ_SYS_TRANS_OP_HALF_TOPIC);
        NOT_ALLOWED_SEND_TOPIC_SET.add(RMQ_SYS_TRANS_CHECK_MAX_TIME_TOPIC);
        NOT_ALLOWED_SEND_TOPIC_SET.add(RMQ_SYS_SELF_TEST_TOPIC);
        NOT_ALLOWED_SEND_TOPIC_SET.add(RMQ_SYS_OFFSET_MOVED_EVENT);
    }

    @AllArgsConstructor
    public static class EnhancedListenerConcurrently implements MessageListenerConcurrently {
        private final MessageListenerConcurrently delegate;

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            return (ConsumeConcurrentlyStatus) new ConsumerDelegation(delegate).onMessage(msgs, context);
        }
    }

    @AllArgsConstructor
    public static class EnhancedListenerOrderly implements MessageListenerOrderly {
        private final MessageListenerOrderly delegate;

        @Override
        public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
            return (ConsumeOrderlyStatus) new ConsumerDelegation(delegate).onMessage(msgs, context);
        }
    }

    private static class ConsumerDelegation {
        private static final String LISTENER_CONTAINER_CLASS_NAME = "org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer";
        private final MessageListener listener;
        private final Class<?> cls;
        private final String method;

        private ConsumerDelegation(MessageListener listener) {
            this.listener = listener;
            Class<?> listenerCls = null;
            Object outerInstance = ReflectUtil.getPropValue(listener, "this$0", true);
            if (outerInstance != null && LISTENER_CONTAINER_CLASS_NAME.equals(outerInstance.getClass().getCanonicalName())) {
                Object realListener = ReflectUtil.getPropValue(outerInstance, "rocketMQListener", true);
                if (realListener != null) {
                    listenerCls = realListener.getClass();
                }
            }
            if (listenerCls == null) {
                listenerCls = listener.getClass();
            }
            this.cls = listenerCls;
            this.method = "onMessage";
        }

        private Object doConsume(List<MessageExt> msgs, Object context) {
            return listener instanceof MessageListenerConcurrently ?
                    ((MessageListenerConcurrently) listener).consumeMessage(msgs, (ConsumeConcurrentlyContext) context) :
                    ((MessageListenerOrderly) listener).consumeMessage(msgs, (ConsumeOrderlyContext) context);
        }

        public Object onMessage(List<MessageExt> msgs, Object c) {
            if (!ComponentEnum.rocketmq_consumer.isEnable() || CollectionUtils.isEmpty(msgs)) {
                return doConsume(msgs, c);
            }
            MoniLogParams params = new MoniLogParams();
            params.setServiceCls(cls);
            params.setAction(method);
            params.setService(ReflectUtil.getSimpleClassName(cls));
            params.setLogPoint(LogPoint.rocketmq_consumer);
            Object result;
            long start = System.currentTimeMillis();
            try {
                params.setInput(formatInputMsgs(msgs));
                MessageExt messageExt = msgs.get(0);
                String[] tags = TagBuilder.of("topic", messageExt.getTopic(), "tag", messageExt.getTags()).toArray();
                params.setTags(tags);
                result = doConsume(msgs, c);
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
        Map<String,Object> msgInfoMap = new HashMap<>();
        try {
            for (MessageExt msg : msgs) {
                String str = new String(msg.getBody(), StandardCharsets.UTF_8);
                JSON json = StringUtil.tryConvert2Json(str);
                obj.add(json == null ? str : json);
            }
            msgInfoMap.put("msgId", msgs.stream().map(MessageExt::getMsgId).collect(Collectors.joining(",")));
            msgInfoMap.put("topic", msgs.stream().map(MessageExt::getTopic).distinct().collect(Collectors.joining(",")));
            obj.add(JSON.toJSONString(msgInfoMap));
        } catch (Exception e) {
            MoniLogUtil.innerDebug("RocketMqMoniLogInterceptor.formatInputMsgs error", e.getMessage());
        }
        return obj.toArray(new Object[0]);
    }

    @Slf4j
    public static class RocketMQProducerEnhanceProcessor implements SendMessageHook {
        private static final String START_TIME = "__mqSend_StartTime";

        @Override
        public String hookName() {
            return this.getClass().getName();
        }

        @Override
        public void sendMessageBefore(SendMessageContext context) {
            if (null == context.getProps()) {
                context.setProps(new HashMap<>());
            }
            context.getProps().put(START_TIME, String.valueOf(System.currentTimeMillis()));
        }

        @Override
        public void sendMessageAfter(SendMessageContext context) {
            // 异步的时候会调用两次sendMessageAfter,第二次的sendResult会有发送结果，取第二次
            if (CommunicationMode.ASYNC == context.getCommunicationMode() && context.getSendResult() == null && context.getException() == null) {
                // 异步且没有结果以及没有异常时，无法判定是否成功
                return;
            }
            if (context.getProps() == null || !context.getProps().containsKey(START_TIME)) {
                return;
            }
            if (!ComponentEnum.rocketmq_producer.isEnable()) {
                return;
            }
            Message message = context.getMessage();
            String topic = message.getTopic();
            // rocketmq内部消息追踪的topic,跳过
            if (isSystemTopic(topic) || isNotAllowedSendTopic(topic)) {
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
                String startTimeStr = context.getProps().get(START_TIME);
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
                    sendResultMap.put("regionId", sendResult.getRegionId());
                    sendResultMap.put("sendStatus", sendResult.getSendStatus());
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

    private static boolean isSystemTopic(String topic) {
        return SYSTEM_TOPIC_SET.contains(topic) || topic.startsWith(SYSTEM_TOPIC_PREFIX);
    }

    private static boolean isNotAllowedSendTopic(String topic) {
        return NOT_ALLOWED_SEND_TOPIC_SET.contains(topic);
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