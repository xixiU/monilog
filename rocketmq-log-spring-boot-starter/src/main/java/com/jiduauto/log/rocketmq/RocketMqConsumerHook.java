package com.jiduauto.log.rocketmq;

import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
class RocketMqConsumerHook implements ConsumeMessageHook {
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
        MonitorLogParams logParams = new MonitorLogParams();
        logParams.setLogPoint(LogPoint.MSG_ENTRY);
        logParams.setAction("consumeMq");
        long startTime = Long.parseLong(context.getProps().get("startTime"));

        logParams.setCost(System.currentTimeMillis() - startTime);
        List<String> tagList = processTag(context);

        List<MessageExt> msgList = context.getMsgList();
        for (MessageExt messageExt: msgList) {
            tagList.add(RocketMQLogConstant.MQ_BODY);
            tagList.add(new String(messageExt.getBody()));
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