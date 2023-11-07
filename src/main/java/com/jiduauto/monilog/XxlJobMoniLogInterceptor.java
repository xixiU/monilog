package com.jiduauto.monilog;


import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Method;


@Aspect
class XxlJobMoniLogInterceptor {
    //处理注解式任务
    @Around("@annotation(com.xxl.job.core.handler.annotation.XxlJob)")
    private Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
        Object result = xxlJobEnable() ? MoniLogAop.processAround(pjp, buildLogParserForJob(), LogPoint.xxljob) : pjp.proceed();
        MoniLogThreadHolder.clear();;
        return result;

    }

    //处理继承式任务
    static IJobHandler getProxyBean(IJobHandler bean) {
        return ProxyUtils.getProxy(bean, invocation -> {
            Method method = invocation.getMethod();
            boolean shouldMonilog = "execute".equals(method.getName()) && xxlJobEnable();
            Object result =  shouldMonilog ? MoniLogAop.processAround(invocation, buildLogParserForJob(), LogPoint.xxljob) : invocation.proceed();
            MoniLogThreadHolder.clear();;
            return result;
        });
    }

    private static LogParser buildLogParserForJob() {
        String boolExpr = "$.code==" + ReturnT.SUCCESS_CODE;
        return LogParser.Default.buildInstance(boolExpr);
    }

    static boolean xxlJobEnable() {
        MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        return moniLogProperties != null && moniLogProperties.isComponentEnable(ComponentEnum.xxljob, moniLogProperties.getXxljob().isEnable());
    }
}