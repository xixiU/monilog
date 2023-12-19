package com.jiduauto.monilog;


import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;

import java.lang.reflect.Method;


public final class XxlJobMoniLogInterceptor {
    //增强xxljob，请勿改动此方法
    public static IJobHandler getProxyBean(IJobHandler bean) {
        return ProxyUtils.getProxy(bean, invocation -> {
            Method method = invocation.getMethod();
            Class<?> returnType = method.getReturnType();
            boolean shouldMonilog = "execute".equals(method.getName()) && ComponentEnum.xxljob.isEnable();
            return shouldMonilog ? MoniLogAop.processAround(invocation, buildLogParserForJob(returnType == ReturnT.class), LogPoint.xxljob) : invocation.proceed();
        });
    }

    private static LogParser buildLogParserForJob(boolean hasReturnT) {
        //注意，新版本的xxljob并不要求xxljob方法签名符合"public ReturnT<String>"模式
        //因为execute方法并不一定总有返回值，因此，如果方法签名中没有ReturnT的返回，那么，只要不异常就得认为是任务执行成功
        if (hasReturnT) {
            return LogParser.Default.buildInstance("$.code==" + ReturnT.SUCCESS_CODE);
        } else {
            return LogParser.Default.buildInstance(ResultParseStrategy.IfNotException, null, null, null);
        }
    }
}