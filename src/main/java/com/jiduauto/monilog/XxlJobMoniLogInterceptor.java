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
        MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        // 判断开关
        if (moniLogProperties == null ||
                !moniLogProperties.isComponentEnable("xxljob", moniLogProperties.getXxljob().isEnable())) {
            return pjp.proceed();
        }
        return MoniLogAop.processAround(pjp, buildLogParserForJob(), LogPoint.xxljob);
    }

    //处理继承式任务
    static IJobHandler getProxyBean(IJobHandler bean) {
        return ProxyUtils.getProxy(bean, invocation -> {
            Method method = invocation.getMethod();
            if ("execute".equals(method.getName())) {
                return MoniLogAop.processAround(invocation, buildLogParserForJob(), LogPoint.xxljob);
            }
            return invocation.proceed();
        });
    }

    private static LogParser buildLogParserForJob() {
        String boolExpr = "$.code==" + ReturnT.SUCCESS_CODE;
        return LogParser.Default.buildInstance(boolExpr);
    }
}