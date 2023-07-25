package com.jiduauto.log.rocketmq.aop;

import com.alibaba.fastjson.JSON;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: mq生产者的切面
 * @author rongjie.yuan
 * @date 2023/7/24 15:09
 */
@Slf4j
@Aspect
public class RocketMqProducerAop {
    @Around("execution(public * org.apache.rocketmq.spring.core.RocketMQTemplate.*(..))")
    public void interceptorRocketMQProducerConvertAndSend(ProceedingJoinPoint pjp) throws Throwable{
        realInterceptor(pjp);
    }

    private void realInterceptor(ProceedingJoinPoint pjp) throws Throwable{
        MonitorLogParams logParams = new MonitorLogParams();
        logParams.setLogPoint(LogPoint.MSG_PRODUCER);

        long startTime = System.currentTimeMillis();

        Class<?> declaringClass = pjp.getTarget().getClass();
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        String methodName = methodSignature.getName();

        logParams.setServiceCls(declaringClass);
        logParams.setService(declaringClass.getSimpleName());
        logParams.setAction(methodName);
        List<String> tagList = new ArrayList<>();
        Object[] args = pjp.getArgs();
        try{
            for (Object arg : args) {
                // 想要获取arg的对象名
                tagList.add(arg.getClass().getSimpleName());
                tagList.add(JSON.toJSONString(arg));
            }
        }catch (Exception e){
            log.error("arg.getClass().getSimpleName() error", e);
        }
        try{
            // 执行目标方法
            pjp.proceed();
            logParams.setSuccess(true);
        }catch (Exception e) {
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
