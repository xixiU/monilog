package com.jiduauto.monitor.log;


import com.xxl.job.core.biz.model.ReturnT;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;


@Aspect
class XxlJobLogMonitorExecuteInterceptor {
    @Around("execution(public * com.xxl.job.core.handler.IJobHandler+.*(..)) || @annotation(com.xxl.job.core.handler.annotation.XxlJob)")
    public Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
        String boolExpr = "$.code==" + ReturnT.SUCCESS_CODE;
        LogParser logParser = LogParser.Default.buildInstance(boolExpr);
        return MonitorLogAop.processAround(pjp, logParser, LogPoint.xxljob);
    }
}