package com.jiduauto.log;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
/**
 * @author yp
 */
@Slf4j
@Aspect
class MonitorLogAop {
    private final MonitorLogPrinter logPrinter;

    public MonitorLogAop(MonitorLogPrinter logPrinter, MonitorLogProperties properties) {
        this.logPrinter = logPrinter;
    }

    @Around("@within(com.jiduauto.log.MonitorLog) || @annotation(com.jiduauto.log.MonitorLog) && execution(public * *(..))")
    public Object doCustom(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, null);
    }

    private Object processAround(ProceedingJoinPoint pjp, LogPoint point) throws Throwable {
        MonitorLogAspectCtx ctx = null;
        Throwable tx = null;
        try {
            try {
                ctx = beforeProcess(pjp, point);
                if (ctx.isIgnore()) {
                    return pjp.proceed();
                }
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
            log.error("MonitorLogAop processAround error", e);
            if (ctx != null && ctx.isHasExecuted()) {
                return ctx.getResult();
            }
            return pjp.proceed();
        }
    }

    private MonitorLogAspectCtx beforeProcess(ProceedingJoinPoint pjp, LogPoint point) {
        return new MonitorLogAspectCtx(pjp, pjp.getArgs(), point);
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
        params.setLogPoint(ctx.getLogPoint());
        params.setService(ctx.parseServiceName());
        params.setAction(ctx.getMethodName());
        params.setSuccess(parsedResult.isSuccess());
        params.setMsgCode(parsedResult.getMsgCode());
        params.setMsgInfo(parsedResult.getMsgInfo());
        params.setCost(ctx.getCost());
        params.setException(ctx.getException());
        params.setInput(ctx.getArgs());
        params.setOutput(ctx.getResult());
        logPrinter.log(params);
    }

    private void beforeReturn(MonitorLogAspectCtx ctx) {
        //...
    }
}
