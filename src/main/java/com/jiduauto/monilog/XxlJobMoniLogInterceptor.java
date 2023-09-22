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
        if (xxlJobEnable()) {
            return MoniLogAop.processAround(pjp, buildLogParserForJob(), LogPoint.xxljob);
        }
        return pjp.proceed();
    }

    //处理继承式任务
    static IJobHandler getProxyBean(IJobHandler bean) {
        return ProxyUtils.getProxy(bean, invocation -> {
            Method method = invocation.getMethod();
            if ("execute".equals(method.getName())) {
                if (xxlJobEnable()) {
                    return MoniLogAop.processAround(invocation, buildLogParserForJob(), LogPoint.xxljob);
                }
                return invocation;
            }
            return invocation.proceed();
        });
    }

    private static LogParser buildLogParserForJob() {
        String boolExpr = "$.code==" + ReturnT.SUCCESS_CODE;
        return LogParser.Default.buildInstance(boolExpr);
    }

    private static boolean xxlJobEnable(){
        MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        // 判断开关
        return moniLogProperties != null && moniLogProperties.isComponentEnable("xxljob", moniLogProperties.getXxljob().isEnable());
    }

}