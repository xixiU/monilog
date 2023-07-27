package com.jiduauto.log.core.aop;

import com.jiduauto.log.core.LogParser;
import com.jiduauto.log.core.MonitorLogAspectCtx;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.parse.ParsedResult;
import com.jiduauto.log.core.util.MonitorLogUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * @author yp
 */
@Slf4j
@Aspect
public class MonitorLogAop {
    @Around("@within(com.jiduauto.log.core.annotation.MonitorLog)")
    Object doAround(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, null, null);
    }

    public static Object processAround(ProceedingJoinPoint pjp, LogParser logParser, LogPoint logPoint) throws Throwable {
        MonitorLogAspectCtx ctx = null;
        Throwable tx = null;
        try {
            try {
                ctx = beforeProcess(pjp, logParser, logPoint);
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
    private static MonitorLogAspectCtx beforeProcess(ProceedingJoinPoint pjp, LogParser logParser, LogPoint logPoint) {
        MonitorLogAspectCtx ctx = new MonitorLogAspectCtx(pjp, pjp.getArgs());
        if (logParser != null) {
            ctx.setLogParserAnnotation(logParser);
        }
        if (logPoint != null) {
            ctx.setLogPoint(logPoint);
        }
        return ctx;
    }

    private static void doProcess(ProceedingJoinPoint pjp, MonitorLogAspectCtx ctx) {
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

    private static void afterProcess(MonitorLogAspectCtx ctx) {
        MonitorLogParams params = new MonitorLogParams();
        ParsedResult parsedResult = ctx.getParsedResult();
        params.setServiceCls(ctx.getMethodOwnedClass());
        params.setLogPoint(ctx.getLogPoint());
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
        try {
            MonitorLogUtil.log(params);
        } catch (Exception e) {
            log.error("logPrinter.log error", e);
        }
    }

    private static void beforeReturn(MonitorLogAspectCtx ctx) {
        //...
    }
}
