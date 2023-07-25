package com.jiduauto.log.xxljob.interceptor;

import com.jiduauto.log.core.aop.MonitorLogAop;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * @author fan.zhang02
 * @date 2023/07/24/11:25
 */
@Aspect
@Slf4j
public class XxlJobLogMonitorExecuteInterceptor {
    @Around("execution(* com.xxl.job.core.handler.IJobHandler+.execute(..))")
    public Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
        return MonitorLogAop.processAround(pjp);
    }
}
