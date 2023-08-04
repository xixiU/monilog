package com.jiduauto.monilog;


import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


@Slf4j
class RedisMoniLogInterceptor {
    static class JedisTemplateInterceptor implements MethodInterceptor {
        private static final long ONE_KB = 2 << 19;
        private static final Set<String> SKIP_METHODS = new HashSet<>();

        static {
            SKIP_METHODS.addAll(Arrays.asList("isPipelined", "close", "isClosed", "getNativeConnection", "isQueueing", "closePipeline"));
        }

        private final RedisSerializer keySerializer;
        private final RedisSerializer valueSerializer;
        private final MoniLogProperties.RedisProperties redisProperties;

        JedisTemplateInterceptor(RedisSerializer keySerializer, RedisSerializer valueSerializer, MoniLogProperties.RedisProperties redisProperties) {
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
                ri.args = deserialize(keySerializer, invocation.getArguments());
                ri.maybeKey = deserializeKey(keySerializer, invocation.getArguments());
                ri.result = deserialize(valueSerializer, new Object[]{ret});
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

        private Object[] deserialize(RedisSerializer serializer, Object[] args) {
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
    }

    static class RedissonInterceptor implements MethodInterceptor {
        private static final Set<String> TARGET_METHODS = new HashSet<>();

        static {
            TARGET_METHODS.addAll(Arrays.asList("transfer", "get", "getNow", "getInterrupted", "getInterrupted"));
        }

        private final MoniLogProperties.RedisProperties redisProperties;

        public RedissonInterceptor(MoniLogProperties.RedisProperties redisProperties) {
            this.redisProperties = redisProperties;
        }

        /**
         * 调用CommandAsyncExecutor的方法
         */
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            String methodName = method.getName();
            if (!TARGET_METHODS.contains(methodName)) {
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