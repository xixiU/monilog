package com.jiduauto.monilog;


import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


@Slf4j
class RedisMoniLogInterceptor {
    @AllArgsConstructor
    static class JedisTemplateInterceptor implements MethodInterceptor {
        private static final long ONE_KB = 2 << 19;
        private static final Set<String> SKIP_METHODS = new HashSet<>();

        static {
            SKIP_METHODS.addAll(Arrays.asList("isPipelined", "close", "isClosed", "getNativeConnection", "isQueueing", "closePipeline"));
        }

        private final RedisSerializer keySerializer;
        private final RedisSerializer valueSerializer;
        private final MoniLogProperties.RedisProperties redisProperties;

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
                p.setService(ri.cls.getSimpleName());
                p.setAction(ri.method);
                if (ri.valueLen > 0 && ri.valueLen > redisProperties.getWarnForValueLength() * ONE_KB) {
                    log.error("redis_value_size_too_large, {}.{}[key={}], size: {}({})", p.getService(), p.getAction(), ri.maybeKey, ri.valueLen, ri.getReadableSize());
                }
                String msgPrefix = "";
                if (StringUtils.isNotBlank(ri.maybeKey)) {
                    msgPrefix = "[key=" + ri.maybeKey + "]";
                }
                p.setMsgInfo(msgPrefix + p.getMsgInfo());
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
                ri.args = deserializeArgs(keySerializer, invocation.getArguments());
                ri.maybeKey = deserializeKey(keySerializer, invocation.getArguments());
                ri.result = deserializeArgs(valueSerializer, new Object[]{ret});
            } catch (Exception e) {
                MoniLogUtil.innerDebug("parseRedisInvocation-deserilize error", e);
            }
            return ri;
        }

        private String deserializeKey(RedisSerializer serializer, Object[] args) {
            if (args == null) {
                return null;
            }
            //取参数中第一个byte[]类型的参数做为猜想的key
            for (Object arg : args) {
                if (arg instanceof byte[]) {
                    Object obj = serializer.deserialize((byte[]) arg);
                    return String.valueOf(obj);
                }
            }
            return null;
        }

        private Object[] deserializeArgs(RedisSerializer serializer, Object[] args) {
            if (serializer == null || args == null) {
                return args;
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
            String maybeKey;
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

    /**
     * RedissonClient是异步的，且在javakit环境下，其client不受spring生命周期约束，因此拦截比较难实现。好在其有一个定义清晰的接口。因此其实的实现思路如下：
     * 1. 基于普通AOP增强RedissonClient，拦截返回结果为RMap,RSet,RList,RBucket,RBuckets,RLock的所有方法：
     * 1.1 正常执行原方法
     * 1.2 对返回结果进行包装和增强,即：放入一个代理结果中去，这个代理结果中保存执行前的时间点信息、调用栈信息
     * 1.3 再监控代理结果的执行
     */
    @Aspect
    @AllArgsConstructor
    static class RedissonInterceptor {
        private final MoniLogProperties.RedisProperties redisProperties;

        @Around("execution(public org.redisson.api.R* org.redisson.api.RedissonClient+.*(..))")
        private Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("拦截到RedissonClient中方法的执行...");
            long start = System.currentTimeMillis();
            Object result = pjp.proceed();
            if (result instanceof RMap
                    || result instanceof RSet
                    || result instanceof RList
                    || result instanceof RBucket
                    || result instanceof RBuckets
                    || result instanceof RLock) {
                System.out.println("返回结果需要被代理一下");
                MoniLogParams p = new MoniLogParams();
                MethodSignature signature = (MethodSignature) pjp.getSignature();
                Method method = signature.getMethod();
                p.setServiceCls(method.getDeclaringClass());
                p.setService(method.getDeclaringClass().getSimpleName());
                p.setAction(method.getName());
                p.setInput(pjp.getArgs());
                p.setCost(start); //取结果时再减掉此值
                p.setSuccess(true);
                p.setLogPoint(LogPoint.redis);
                p.setMsgCode(ErrorEnum.SUCCESS.name());
                p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                return ProxyUtils.getProxy(result, new RedissonResultProxy(p));
            }
            return result;
        }
    }

    @AllArgsConstructor
    private static class RedissonResultProxy implements MethodInterceptor {
        private static final Set<String> TARGET_METHODS = Sets.newHashSet(
                "get", "getAndDelete", "getAndSet", "getAndExpire", "getAndClearExpire",
                "put", "putIfAbsent", "putIfExists", "randomEntries", "randomKeys", "addAndGet", "containsKey", "containsValue", "remove", "replace", "putAll",
                "fastPut", "fastRemove", "fastReplace", "fastPutIfAbsent", "fastPutIfExists", "readAllKeySet", "readAllValues", "readAllEntrySet", "readAllMap",
                "keySet", "values", "entrySet", "addAfter", "addBefore", "fastSet", "readAll", "range", "random", "removeRandom", "tryAdd",
                "set", "trySet", "setAndKeepTTL", "setIfAbsent", "setIfExists", "compareAndSet", "tryLock", "lock", "tryLock", "lockInterruptibly"
        );
        private final MoniLogParams p;

        /**
         * 调用CommandAsyncExecutor的方法
         */
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            String methodName = method.getName();
            if (!TARGET_METHODS.contains(methodName) || p == null) {
                return invocation.proceed();
            }
            Object ret;
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
                p.setCost(System.currentTimeMillis() - p.getCost());
//                RedisInvocation ri = parseRedisInvocation(invocation, ret);
//                p.setInput(ri.args);
//                p.setOutput(ri.result);
//                p.setServiceCls(ri.cls);
//                p.setService(ri.cls.getSimpleName());
//                p.setAction(ri.method);
//                if (ri.valueLen > 0 && ri.valueLen > redisProperties.getWarnForValueLength() * ONE_KB) {
//                    log.error("redis_value_size_too_large, {}.{}[key={}], size: {}({})", p.getService(), p.getAction(), ri.maybeKey, ri.valueLen, ri.getReadableSize());
//                }
                String msgPrefix = "";
//                if (StringUtils.isNotBlank(ri.maybeKey)) {
//                    msgPrefix = "[key=" + ri.maybeKey + "]";
//                }
                p.setMsgInfo(msgPrefix + p.getMsgInfo());
                MoniLogUtil.log(p);
            }
        }
    }
}