package com.jiduauto.log;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.List;

/**
 * @author yp
 */
@Slf4j
@Aspect
class MonitorLogAop {
    private final MonitorLogPrinter logPrinter;

    private final MonitorLogProperties properties;

    public MonitorLogAop(MonitorLogPrinter logPrinter, MonitorLogProperties properties) {
        this.logPrinter = logPrinter;
        this.properties = properties;
    }

    private String dao() {
        return join(properties.getDaoAopExpressions());
    }

    private String rpc() {
        return join(properties.getRpcAopExpressions());
    }

    private String mqConsumer() {
        return join(properties.getMqConsumerAopExpressions());
    }

    private String mqProducer() {
        return join(properties.getMqProducerAopExpressions());
    }

    private String http() {
        return join(properties.getHttpAopExpressions());
    }

    private String job() {
        return join(properties.getJobAopExpressions());
    }

    private String custom() {
        return "@within(com.jiduauto.log.MonitorLog) ||@annotation(com.jiduauto.log.MonitorLog) && execution(public * *(..))";
    }

    @Around("execution(String http())")
    public Object doHttp(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.WEB_ENTRY);
    }

    @Around("execution(String rpc())")
    public Object doRpc(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.REMOTE_CLIENT);
    }

    @Around("execution(String mqConsumer())")
    public Object doMqConsumer(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.MSG_ENTRY);
    }

    @Around("execution(String mqProducer())")
    public Object doMqProducer(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.MSG_PRODUCER);
    }

    @Around("execution(String dao())")
    public Object doDao(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.DAL_CLIENT);
    }

    @Around("execution(String job())")
    public Object doJob(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.TASK_ENTRY);
    }

    @Around("execution(String custom())")
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


    private static String join(List<String> patterns) {
        return patterns == null || patterns.isEmpty() ? "" : String.join("||", patterns);
    }
}
