package com.jiduauto.log.rocketmqlogspringbootstart.interceptor;

import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingUtil;

import java.net.InetAddress;

public class RocketMQSendInterceptor implements SendMessageHook {

    @Override
    public String hookName() {
        return RocketMQSendInterceptor.class.getName();
    }

    @Override
    public void sendMessageBefore(SendMessageContext context) {
        // 在发送消息之前拦截，记录开始时间
        context.getProps().put("startTime", String.valueOf(System.currentTimeMillis()));
    }

    @Override
    public void sendMessageAfter(SendMessageContext context) {
        // 在发送完成后拦截，计算耗时并打印监控信息
        try {
            Message message = context.getMessage();
            SendResult sendResult = context.getSendResult();
            long startTime = Long.parseLong(context.getProps().get("startTime"));
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;

            InetAddress localAddr = InetAddress.getByName(RemotingUtil.getLocalAddress());
            String localIp = localAddr != null ? localAddr.getHostAddress() : "unknown";

            System.out.println("RocketMQ Message Monitor:");
            System.out.println("Topic: " + message.getTopic());
            System.out.println("Service: " + context.getProducerGroup());
            System.out.println("Method: SEND");
            System.out.println("IP: " + localIp);
            System.out.println("Elapsed Time: " + elapsedTime + " ms");
            System.out.println("Send Result: " + sendResult.getSendStatus());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}