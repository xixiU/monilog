package com.jiduauto.monilog;


import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.MDC;

import java.lang.reflect.Method;


public final class XxlJobMoniLogInterceptor {
    //增强xxljob，请勿改动此方法
    public static IJobHandler getProxyBean(IJobHandler bean) {
        return ProxyUtils.tryGetProxy(bean, invocation -> {
            Method method = invocation.getMethod();
            Class<?> returnType = method.getReturnType();
            boolean shouldMonilog = "execute".equals(method.getName()) && ComponentEnum.xxljob.isEnable();
            if (!shouldMonilog) {
                return invocation.proceed();
            }
            //必须使用javakit-xxljob才会自动提供traceId，否则会没有traceId，这里按javakit-xxljob相同的方式统一生成traceId
            Tracer tracer = GlobalOpenTelemetry.getTracer(getTraceInstrument(invocation.getThis().getClass()), "0.0.1");
            Span span = tracer.spanBuilder("xxl-job").startSpan();
            MDC.put("trace_id", span.getSpanContext().getTraceId());
            MDC.put("span_id", span.getSpanContext().getSpanId());
            try (Scope ignore = span.makeCurrent()) {
                return MoniLogAop.processAround(invocation, buildLogParserForJob(returnType == ReturnT.class), LogPoint.xxljob);
            } catch (Throwable t) {
                span.recordException(t);
                throw t;
            } finally {
                span.end();
            }
        }, IJobHandler.class);
    }

    private static String getTraceInstrument(Class<?> cls) {
        String pkg = cls.getPackage().getName();
        return pkg.split("job")[0] + "job";
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