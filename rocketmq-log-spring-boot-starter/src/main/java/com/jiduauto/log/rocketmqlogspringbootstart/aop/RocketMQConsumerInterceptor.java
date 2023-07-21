package com.jiduauto.log.rocketmqlogspringbootstart.aop;

import com.jiduauto.log.enums.LogPoint;
import com.jiduauto.log.model.MonitorLogParams;
import com.jiduauto.log.rocketmqlogspringbootstart.constant.RocketMQLogConstant;
import com.jiduauto.log.util.MonitorLogUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author rongjie.yuan
 * @description: 对rocketmq消费者拦截
 * @date 2023/7/21 17:25
 */
@Aspect
@Component
public class RocketMQConsumerInterceptor {

    @Around("execution(* org.apache.rocketmq.spring.core.RocketMQListener.onMessage(..))")
    public void interceptRocketMQConsumer(ProceedingJoinPoint pjp) throws Throwable {
        MonitorLogParams logParams = new MonitorLogParams();
        logParams.setLogPoint(LogPoint.MSG_ENTRY);
        // 获取目标方法的相关信息
        Object[] args = pjp.getArgs();
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        Method method = methodSignature.getMethod();

        Class<?> declaringClass = method.getDeclaringClass();
        RocketMQMessageListener listenerAnnotation = declaringClass.getAnnotation(RocketMQMessageListener.class);

        long startTime = System.currentTimeMillis();

        List<String> tagList = new ArrayList<>();
        tagList.add(RocketMQLogConstant.TOPIC);
        tagList.add(listenerAnnotation.topic());
        if (StringUtils.isNotBlank(listenerAnnotation.consumerGroup())) {
            tagList.add(RocketMQLogConstant.GROUP);
            tagList.add(listenerAnnotation.consumerGroup());
        }
        if (listenerAnnotation.selectorType() != null) {
            tagList.add(RocketMQLogConstant.SELECTOR_TYPE);
            tagList.add(listenerAnnotation.selectorType().name());
        }
        if (StringUtils.isNotBlank(listenerAnnotation.selectorExpression())) {
            tagList.add(RocketMQLogConstant.SELECTOR_EXPRESSION);
            tagList.add(listenerAnnotation.selectorExpression());
        }
        MessageExt messageExt = (MessageExt) args[0];

        tagList.add(RocketMQLogConstant.mqBody);
        tagList.add(new String(messageExt.getBody()));

        logParams.setServiceCls(declaringClass);
        logParams.setService(declaringClass.getSimpleName());
        logParams.setAction(method.getName());
        try {
            // 执行目标方法
            pjp.proceed();
            logParams.setSuccess(true);

        } catch (Exception e) {
            logParams.setSuccess(false);
            logParams.setException(e);
            throw e;
        } finally {
            logParams.setTags(tagList.toArray(new String[0]));
            logParams.setCost(System.currentTimeMillis() - startTime);
            MonitorLogUtil.log(logParams);

        }


    }

}