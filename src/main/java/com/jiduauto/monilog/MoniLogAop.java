package com.jiduauto.monilog;


import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author yp
 */
@Slf4j
@Aspect
class MoniLogAop {
    @Around("@within(com.jiduauto.monilog.MoniLog) || @annotation(com.jiduauto.monilog.MoniLog)")
    private Object doAround(ProceedingJoinPoint pjp) throws Throwable {
        return processAround(pjp, null, null);
    }

    static Object processAround(MethodInvocation invocation, LogParser logParser, LogPoint logPoint) throws Throwable {
        return processAround(InvocationProxy.of(invocation), logParser, logPoint);
    }

    private static Object processAround(ProceedingJoinPoint pjp, LogParser logParser, LogPoint logPoint) throws Throwable {
        return processAround(InvocationProxy.of(pjp), logParser, logPoint);
    }

    private static Object processAround(InvocationProxy proxy, LogParser logParser, LogPoint logPoint) throws Throwable {
        MoniLogAspectCtx ctx = null;
        Throwable tx = null;
        try {
            try {
                ctx = beforeProcess(proxy, logParser, logPoint);
                doProcess(proxy, ctx);
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
            MoniLogUtil.innerDebug("MoniLogAop processAround error", e);
            if (ctx != null && ctx.isHasExecuted()) {
                return ctx.getResult();
            }
            return proxy.getExecutable().proceed();
        }
    }

    private static MoniLogAspectCtx beforeProcess(InvocationProxy proxy, LogParser logParser, LogPoint logPoint) {
        MoniLogAspectCtx ctx = new MoniLogAspectCtx(proxy);
        if (logParser != null) {
            ctx.setLogParserAnnotation(logParser);
        }
        if (logPoint != null) {
            ctx.setLogPoint(logPoint);
        }
        return ctx;
    }

    private static void doProcess(InvocationProxy proxy, MoniLogAspectCtx ctx) {
        Object result = null;
        Throwable ex = null;
        long start = System.currentTimeMillis();
        try {
            result = proxy.getExecutable().proceed();
        } catch (Throwable e) {
            ex = e;
        }
        ctx.buildResult(System.currentTimeMillis() - start, result, ex);
    }

    private static void afterProcess(MoniLogAspectCtx ctx) {
        MoniLogParams params = new MoniLogParams();
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
        params.setUserMetricName(ctx.getMetricName());
        if (ctx.getTags() != null && ctx.getTags().length > 0) {
            // 处理输入参数
            params.setUserTags(processUserTag(ctx.getArgs(), ctx.getTags()));
            if (ctx.getResult() instanceof String) {
                params.setUserTags(StringUtil.processUserTag((String) ctx.getResult(), params.getUserTags()));
            } else {
                params.setUserTags(StringUtil.processUserTag(JSON.toJSONString(ctx.getResult()), params.getUserTags()));
            }
            params.setUserTags(processUserTag(ctx.getArgs(), params.getUserTags()));
        }
        try {
            MoniLogUtil.log(params);
        } catch (Exception e) {
            MoniLogUtil.innerDebug("MoniLogAop afterProcess error", e);
        }
    }

    /**
     * 处理用户自定义tag
     */
    private static String[] processUserTag(Object[] input, String[] oriTags) {
        try {
            if (oriTags == null || oriTags.length == 0 || input == null || input.length == 0) {
                return oriTags;
            }
            // 默认只解析第一个参数里面的对象，多个参数没法确定解析顺序
            // 如果第一个参数是字符串，尝试直接从字符串中提取，注意字符串需要是json格式，因为变量的名称在编译时会替换
            if (input[0] instanceof String) {
                return StringUtil.processUserTag((String) input[0], oriTags);
            }
            Map<String, String> jsonMap = StringUtil.tryConvert2Map(JSON.toJSONString(input[0]));
            return StringUtil.processUserTag(jsonMap, oriTags);
        } catch (Exception e) {
            // 处理错误异常吞掉
            MoniLogUtil.innerDebug("MoniLogAop processUserTag error", e);
            return oriTags;
        }
    }

    private static void beforeReturn(MoniLogAspectCtx ctx) {
        //...
    }

    @Getter
    @Setter
    static class InvocationProxy {
        private Method method;
        private Object target;
        private Object[] args;
        private Executable executable;

        static InvocationProxy of(ProceedingJoinPoint pjp) {
            InvocationProxy proxy = new InvocationProxy();
            proxy.setTarget(pjp.getTarget());
            proxy.setMethod(((MethodSignature) pjp.getSignature()).getMethod());
            proxy.setArgs(pjp.getArgs());
            proxy.setExecutable(pjp::proceed);
            return proxy;
        }

        static InvocationProxy of(MethodInvocation invocation) {
            InvocationProxy proxy = new InvocationProxy();
            proxy.setTarget(invocation.getThis());
            proxy.setMethod(invocation.getMethod());
            proxy.setArgs(invocation.getArguments());
            proxy.setExecutable(invocation::proceed);
            return proxy;
        }
    }

    @FunctionalInterface
    interface Executable {
        Object proceed() throws Throwable;
    }
}
