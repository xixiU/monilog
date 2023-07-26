package com.jiduauto.log.rocketmq;

import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.rocketmq.client.consumer.listener.ConsumeReturnType;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
class RocketMQConsumerPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DefaultRocketMQListenerContainer) {
            DefaultRocketMQListenerContainer container = (DefaultRocketMQListenerContainer) bean;
            // 进行属性填充
            container.getConsumer().getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new RocketMqConsumerHook());
        }
        return bean;
    }


    static class RocketMqConsumerHook implements ConsumeMessageHook {
        @Override
        public String hookName() {
            return RocketMqConsumerHook.class.getName();
        }

        @Override
        public void consumeMessageBefore(ConsumeMessageContext context) {
            if (MapUtils.isEmpty(context.getProps())) {
                context.setProps(new HashMap<>());
            }
            context.getProps().put("startTime", String.valueOf(System.currentTimeMillis()));
        }

        @Override
        public void consumeMessageAfter(ConsumeMessageContext context) {
            String startTimeProp = context.getProps().get("startTime");
            String returnType = context.getProps().get(MixAll.CONSUME_CONTEXT_TYPE);
            boolean hasException = ConsumeReturnType.EXCEPTION.name().equals(returnType);
            long startTime = NumberUtils.isCreatable(startTimeProp) ? Long.parseLong(startTimeProp) : 0L;

            MonitorLogParams logParams = new MonitorLogParams();
            logParams.setLogPoint(LogPoint.MSG_ENTRY);

            //TODO 以下几项待补充
            logParams.setServiceCls(null);
            logParams.setService("");
            logParams.setAction("consumeMessage");
            logParams.setInput(null);
            logParams.setOutput(null);

            logParams.setCost(System.currentTimeMillis() - startTime);
            logParams.setSuccess(!hasException && context.isSuccess());
            logParams.setMsgCode(context.getStatus());
            logParams.setMsgInfo(StringUtils.isBlank(returnType) ? context.getStatus() : returnType);
            if (hasException) {
                logParams.setException(new MQClientException(returnType, null));
            }

            List<String> tagList = processTag(context);
            List<MessageExt> msgList = context.getMsgList();
            for (MessageExt messageExt : msgList) {
                //TODO 放到output中去， 另外需要new一个对象
                tagList.add(RocketMQLogConstant.MQ_BODY);
                tagList.add(new String(messageExt.getBody(), StandardCharsets.UTF_8));
                logParams.setTags(tagList.toArray(new String[0]));
                MonitorLogUtil.log(logParams);
            }
        }

        private List<String> processTag(ConsumeMessageContext context) {
            List<String> tagList = new ArrayList<>();

            tagList.add(RocketMQLogConstant.TOPIC);
            tagList.add(context.getMsgList().get(0).getTopic());

            tagList.add(RocketMQLogConstant.GROUP);
            tagList.add(context.getConsumerGroup());

            tagList.add(RocketMQLogConstant.STATUS);
            tagList.add(context.getStatus());
            return tagList;
        }
    }
}