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

import java.util.ArrayList;
import java.util.List;

/**
 * @author rongjie.yuan
 * @description: 对rocketmq消费者拦截
 * @date 2023/7/21 17:25
 */
@Aspect
public class RocketMQConsumerAop {

    @Around("execution(* org.apache.rocketmq.spring.core.RocketMQListener.onMessage(..))")
    public void interceptorRocketMQConsumer(ProceedingJoinPoint pjp) throws Throwable {
        MonitorLogParams logParams = new MonitorLogParams();
        logParams.setLogPoint(LogPoint.MSG_ENTRY);

        // 获取目标方法的相关信息
        Object[] args = pjp.getArgs();
        MessageExt messageExt = (MessageExt) args[0];
        Class<?> declaringClass = getDeclaringClass(pjp);

        long startTime = System.currentTimeMillis();

        List<String> tagList = new ArrayList<>();
        tagList.add(RocketMQLogConstant.TOPIC);
        tagList.add(messageExt.getTopic());

        tagList.add(RocketMQLogConstant.mqBody);
        tagList.add(new String(messageExt.getBody()));
        processRocketMQTag(declaringClass, tagList);

        logParams.setServiceCls(declaringClass);
        logParams.setService(declaringClass.getSimpleName());
        logParams.setAction("onMessage");
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

    /**
     * 获取打了RocketMQMessageListener注解的类，填充注解信息到tag
     * @param declaringClass
     * @param tagList
     */
    private void processRocketMQTag(Class<?> declaringClass, List<String> tagList) {
        RocketMQMessageListener listenerAnnotation = declaringClass.getAnnotation(RocketMQMessageListener.class);
        if (listenerAnnotation == null) {
            return;
        }
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
    }

    private Class<?> getDeclaringClass(ProceedingJoinPoint pjp){
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        Class<?> declaringClass = methodSignature.getDeclaringType();
        // 一般rocketmq都是直接在RocketMQListener.onMessage实现这个接口的方法上打注解
        // 先找切入点方法上的类名即直接找RocketMQListener.onMessage上的注解
        if (declaringClass.isAnnotationPresent(RocketMQMessageListener.class)){
            return declaringClass;
        }
        // 找不到再找切入点目前对象所在类上的注解,切入点目前对象类上没有注解就找父类一直不停获取
        Class<?> aClass = pjp.getTarget().getClass();
        while (aClass != null && !aClass.isAnnotationPresent(RocketMQMessageListener.class)) {
            aClass = aClass.getSuperclass();
        }
        return aClass != null ? aClass : declaringClass;
    }


}