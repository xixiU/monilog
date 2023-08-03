package com.jiduauto.monilog;


import com.xxl.job.core.biz.model.ReturnT;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;


@Aspect
class XxlJobMoniLogInterceptor {
    @Around("execution(public * com.xxl.job.core.handler.IJobHandler+.execute(..)) || @annotation(com.xxl.job.core.handler.annotation.XxlJob)")
    public Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
        String boolExpr = "$.code==" + ReturnT.SUCCESS_CODE;
        LogParser logParser = LogParser.Default.buildInstance(boolExpr);
        return MoniLogAop.processAround(pjp, logParser, LogPoint.xxljob);
    }
}