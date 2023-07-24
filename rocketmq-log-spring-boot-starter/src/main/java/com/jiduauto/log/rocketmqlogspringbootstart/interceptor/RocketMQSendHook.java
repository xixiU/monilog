package com.jiduauto.log.rocketmqlogspringbootstart.interceptor;

import com.alibaba.fastjson.JSON;
import com.jiduauto.log.enums.LogPoint;
import com.jiduauto.log.model.MonitorLogParams;
import com.jiduauto.log.rocketmqlogspringbootstart.constant.RocketMQLogConstant;
import com.jiduauto.log.util.MonitorLogUtil;
import org.apache.commons.collections.MapUtils;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.common.message.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RocketMQSendHook implements SendMessageHook {

    @Override
    public String hookName() {
        return RocketMQSendHook.class.getName();
    }

    @Override
    public void sendMessageBefore(SendMessageContext context) {
        if (MapUtils.isEmpty(context.getProps())) {
            context.setProps(new HashMap<>());
        }
        // 在发送消息之前拦截，记录开始时间
        context.getProps().put("startTime", String.valueOf(System.currentTimeMillis()));
    }

    @Override
    public void sendMessageAfter(SendMessageContext context) {
        // 在发送完成后拦截，计算耗时并打印监控信息
        MonitorLogParams logParams = new MonitorLogParams();
        logParams.setLogPoint(LogPoint.MSG_PRODUCER);
        logParams.setAction("produceMq");

        try {

            List<String> tagList = processTag(context);

            long startTime = Long.parseLong(context.getProps().get("startTime"));
            logParams.setCost(System.currentTimeMillis() - startTime);
            logParams.setException(context.getException());
            logParams.setTags(tagList.toArray(new String[0]));
            MonitorLogUtil.log(logParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> processTag(SendMessageContext context) {
        Message message = context.getMessage();
        List<String> tagList = new ArrayList<>();
        tagList.add(RocketMQLogConstant.SEND_RESULT);
        tagList.add(JSON.toJSONString(context.getSendResult()));

        tagList.add(RocketMQLogConstant.TOPIC);
        tagList.add(message.getTopic());

        tagList.add(RocketMQLogConstant.GROUP);
        tagList.add(context.getProducerGroup());

        tagList.add(RocketMQLogConstant.MQ_BODY);
        tagList.add(new String(message.getBody()));
        return tagList;
    }
}