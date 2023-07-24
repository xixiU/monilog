package com.jiduauto.log.xxljobspringbootstarter.interceptor;

import com.google.common.base.Preconditions;
import com.jiduauto.log.MonitorLogAspectCtx;
import com.jiduauto.log.MonitorLogPrinter;
import com.jiduauto.log.enums.LogPoint;
import com.jiduauto.log.model.MonitorLogParams;
import com.jiduauto.log.parse.ParsedResult;
import com.jiduauto.log.util.MonitorLogUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @author fan.zhang02
 * @date 2023/07/24/11:25
 */
@Aspect
@Slf4j
@Component
public class XxlJobLogMonitorExecuteInterceptor {
    private final MonitorLogPrinter logPrinter;

    public XxlJobLogMonitorExecuteInterceptor(MonitorLogPrinter logPrinter) {
        this.logPrinter = logPrinter;
    }
    @Around("execution(* com.xxl.job.core.handler.IJobHandler.execute(..))")
    public Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp);
    }

    private Object processAround(ProceedingJoinPoint pjp) throws Throwable {

        MonitorLogAspectCtx ctx = null;
        Throwable tx = null;
        try {
            try {
                ctx = beforeProcess(pjp);
                doProcess(pjp, ctx);
                afterProcess(ctx);
                tx = ctx.getException();
                if (tx != null) {
                    throw tx;
                }
                return ctx.getResult();
            } finally {
                beforeReturn(ctx);
            }
        } catch (Throwable e) {
            if (tx != null && e == tx) {
                throw e;
            }
            log.error("AspectProcessor processAround error", e);
            if (ctx != null && ctx.isHasExecuted()) {
                return ctx.getResult();
            }
            return pjp.proceed();
        }
    }


    private MonitorLogAspectCtx beforeProcess(ProceedingJoinPoint pjp) {
        return new MonitorLogAspectCtx(pjp, pjp.getArgs());
    }

    private void doProcess(ProceedingJoinPoint pjp, MonitorLogAspectCtx ctx) {
        Object result = null;
        Throwable ex = null;
        long start = System.currentTimeMillis();
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            ex = e;
        }
        ctx.buildResult(System.currentTimeMillis() - start, result, ex);
    }

    private void afterProcess(MonitorLogAspectCtx ctx) {
        MonitorLogParams params = new MonitorLogParams();
        ParsedResult parsedResult = ctx.getParsedResult();
        params.setServiceCls(ctx.getMethodOwnedClass());
        params.setLogPoint(LogPoint.TASK_ENTRY);
        params.setTags(ctx.getTags());
        params.setService(ctx.parseServiceName());
        params.setAction(ctx.getMethodName());
        params.setSuccess(parsedResult.isSuccess());
        params.setMsgCode(parsedResult.getMsgCode());
        params.setMsgInfo(parsedResult.getMsgInfo());
        params.setCost(ctx.getCost());
        params.setException(ctx.getException());
        params.setInput(ctx.getArgs());
        params.setOutput(ctx.getResult());

        MonitorLogUtil.log(params);
    }

    private void beforeReturn(MonitorLogAspectCtx ctx) {
        //...
    }
}
