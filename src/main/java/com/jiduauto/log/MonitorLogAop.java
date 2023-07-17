package com.jiduauto.log;

import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.checkerframework.checker.units.qual.A;

/**
 * @author yp
 */
@Slf4j
@Aspect
class MonitorLogAop {
    private final MonitorLogPrinter logPrinter;

    public MonitorLogAop(MonitorLogPrinter logPrinter) {
        this.logPrinter = logPrinter;
    }

    @Pointcut("(@target(org.springframework.cloud.openfeign.FeignClient) || @within(org.springframework.cloud.openfeign.FeignClient)) && execution(public * *(..))")
    private void feign() {
    }

    @Pointcut("execution(public * (@org.springframework.web.bind.annotation.RestController *+).*(..)) || execution(public * (@org.springframework.stereotype.Controller *+).*(..)) || execution(public * (@org.springframework.web.servlet.mvc.Controller *+).*(..))")
    private void httpController() {
    }

    @Pointcut("@annotation(io.grpc.stub.annotations.RpcMethod)")
    private void grpc() {}

    @Pointcut("execution(public * com.baomidou.mybatisplus.core.mapper.Mapper+.*(..)) || execution(public * com.baomidou.mybatisplus.extension.service.IService+.*(..))")
    private void mapper() {
    }

    @Pointcut("execution(public * org.apache.rocketmq.client.consumer.listener.MessageListener+.*(..))")
    private void rocketMq() {
    }

    @Pointcut("execution(public * org.apache.rocketmq.client.producer.DefaultMQProducer.send(..))")
    private void rocketMqSend(){}

    @Pointcut("execution(public * com.xxl.job.core.handler.IJobHandler+.execute(..))")
    private void xxljob() {
    }

    /**
     * 声明指定注解标的服务接口的实现类的公共方法为切点, 优先级最高
     */
    @Pointcut("(@target(com.jiduauto.log.MonitorLog) || @within(com.jiduauto.log.MonitorLog) || @annotation(com.jiduauto.log.MonitorLog)) && execution(public * *(..))")
    private void custom() {
    }

    @Around("httpController()")
    public Object doHttpAround(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.WEB_ENTRY);
    }

    @Around("feign() || grpc()")
    public Object doRpcAround(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.REMOTE_CLIENT);
    }

    @Around("rocketMq()")
    public Object doMsgAround(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.MSG_ENTRY);
    }

    @Around("rocketMqSend()")
    public Object doMsgSendAround(ProceedingJoinPoint pjp)throws Throwable{
        return processAround(pjp, LogPoint.MSG_PRODUCER);
    }

    @Around("mapper()")
    public Object doDaoAround(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.DAL_CLIENT);
    }

    @Around("xxljob()")
    public Object doJobAroud(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, LogPoint.TASK_ENTRY);
    }

    @Around("custom()")
    public Object doAround(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, null);
    }

    private Object processAround(ProceedingJoinPoint pjp, LogPoint point) throws Throwable {
        MonitorLogAspectCtx ctx = null;
        Throwable tx = null;
        try {
            try {
                ctx = beforeProcess(pjp, point);
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
