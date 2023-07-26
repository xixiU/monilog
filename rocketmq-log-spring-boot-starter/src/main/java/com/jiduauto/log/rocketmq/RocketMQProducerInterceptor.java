package com.jiduauto.log.rocketmq;

import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.jiduauto.log.core.util.TagBuilder;
import com.jiduauto.log.core.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

@Slf4j
class RocketMQProducerInterceptor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DefaultMQProducer) {
            DefaultMQProducer producer = (DefaultMQProducer) bean;
            producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMQSendHook());
        }
        return bean;
    }


    static class RocketMQSendHook implements SendMessageHook {
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
            MonitorLogParams logParams = new MonitorLogParams();
            logParams.setLogPoint(LogPoint.MSG_PRODUCER);
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
                logParams.setSuccess(context.getException() != null && status == SendStatus.SEND_OK);
                logParams.setMsgCode(status == null ? ErrorEnum.FAILED.name() : status.name());
                Message message = context.getMessage();
                logParams.setInput(new Object[]{new String(message.getBody(), StandardCharsets.UTF_8)});
                logParams.setTags(TagBuilder.of("topic", message.getTopic(), "group", context.getProducerGroup(), "tag", message.getTags()).toArray());
                MonitorLogUtil.log(logParams);
            } catch (Exception e) {
                log.error("sendMessageAfter error", e);
            }
        }
    }
}
