package com.jiduauto.monilog;


import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


@Slf4j
class RedisMoniLogInterceptor implements MethodInterceptor {
    private static final long ONE_KB = 2 << 20;
    private static final Set<String> SKIP_METHODS = new HashSet<>();

    static {
        SKIP_METHODS.addAll(Arrays.asList("isPipelined", "close", "isClosed", "getNativeConnection", "isQueueing", "closePipeline"));
    }

    private final RedisSerializer keySerializer;
    private final RedisSerializer valueSerializer;
    private final MoniLogProperties.RedisProperties redisProperties;

    RedisMoniLogInterceptor(RedisSerializer keySerializer, RedisSerializer valueSerializer, MoniLogProperties.RedisProperties redisProperties) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.redisProperties = redisProperties;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        String methodName = method.getName();
        if (SKIP_METHODS.contains(methodName)) {
            return invocation.proceed();
        }
        Object target = invocation.getThis();
        String serviceName = target.getClass().getSimpleName();

        MoniLogParams p = new MoniLogParams();
        p.setServiceCls(target.getClass());
        p.setService(serviceName);
        p.setAction(methodName);
        p.setSuccess(true);
        p.setLogPoint(LogPoint.redis);
        p.setMsgCode(ErrorEnum.SUCCESS.name());
        p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
        long start = System.currentTimeMillis();
        Object ret = null;
        try {
            ret = invocation.proceed();
            return ret;
        } catch (Throwable e) {
            p.setException(e);
            p.setSuccess(false);
            ErrorInfo errorInfo = ExceptionUtil.parseException(e);
            p.setMsgCode(errorInfo.getErrorCode());
            p.setMsgInfo(errorInfo.getErrorMsg());
            throw e;
        } finally {
            p.setCost(System.currentTimeMillis() - start);
            RedisInvocation ri = parseRedisInvocation(invocation, ret);
            p.setInput(ri.args);
            p.setOutput(ri.result);
            p.setServiceCls(ri.cls);
            p.setAction(ri.method);
            p.setService(ri.cls.getSimpleName());
            if (ri.valueLen > 0 && ri.valueLen > redisProperties.getWarnForValueLength() * ONE_KB) {
                log.error("redis_value_size_too_large, {}.{}, size: {}", p.getService(), p.getAction(), ri.getReadableSize());
            }
            MoniLogUtil.log(p);
        }
    }

    private RedisInvocation parseRedisInvocation(MethodInvocation invocation, Object ret) {
        RedisInvocation ri = new RedisInvocation();
        try {
            StackTraceElement st = ThreadUtil.getNextClassFromStack(RedisTemplate.class, "org.springframework");
            ri.cls = Class.forName(st.getClassName());
            ri.method = st.getMethodName();
        } catch (Exception ignore) {
            ri.cls = invocation.getThis().getClass();
            ri.method = invocation.getMethod().getName();
        }
        try {
            ri.valueLen = ret instanceof byte[] ? ((byte[]) ret).length : 0f;
            ri.args = deserialize(keySerializer, invocation.getArguments());
            ri.result = deserialize(valueSerializer, new Object[]{ret});
        } catch (Exception e) {
            MoniLogUtil.innerDebug("parseRedisInvocation-deserilize error", e);
        }
        return ri;
    }

    private Object[] deserialize(RedisSerializer serializer, Object[] args) {
        if (serializer == null) {
            return args;
        }
        if (args == null) {
            return null;
        }
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof byte[]) {
                result[i] = serializer.deserialize((byte[]) arg);
            } else {
                result[i] = arg;
            }
        }
        return result;
    }


    private static class RedisInvocation {
        Class<?> cls;
        String method;
        Object[] args;
        Object result;
        float valueLen;

        String getReadableSize() {
            if (valueLen <= 0) {
                return "0 B";
            }
            String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int digitGroups = (int) (Math.log10(valueLen) / Math.log10(1024));
            return String.format("%.1f %s", valueLen / Math.pow(1024, digitGroups), units[digitGroups]);
        }
    }
}