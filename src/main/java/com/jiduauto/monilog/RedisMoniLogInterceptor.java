package com.jiduauto.monilog;


import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.*;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
public class RedisMoniLogInterceptor {
    private static final Set<String> SKIP_METHODS_FOR_REDIS = Sets.newHashSet("isPipelined", "close", "isClosed", "getNativeConnection", "isQueueing", "closePipeline", "evaluate");
    private static final Set<String> TARGET_REDISSON_METHODS = Sets.newHashSet("get", "getAndDelete", "getAndSet", "getAndExpire", "getAndClearExpire", "put", "putIfAbsent", "putIfExists", "randomEntries", "randomKeys", "addAndGet", "containsKey", "containsValue", "remove", "replace", "putAll", "fastPut", "fastRemove", "fastReplace", "fastPutIfAbsent", "fastPutIfExists", "readAllKeySet", "readAllValues", "readAllEntrySet", "readAllMap", "keySet", "values", "entrySet", "addAfter", "addBefore", "fastSet", "readAll", "range", "random", "removeRandom", "tryAdd", "set", "trySet", "setAndKeepTTL", "setIfAbsent", "setIfExists", "compareAndSet", "tryLock", "lock", "tryLock", "lockInterruptibly");
    private static final RedisSerializer<?> stringSerializer = new StringRedisSerializer();
    private static final RedisSerializer<?> jdkSerializer = new JdkSerializationRedisSerializer();

    /**
     * RedisConnectionFactory的实现类有LettuceConnectionFactory，LettuceConnectionFactory，对这两增强
     */
    public static class RedisConnectionFactoryInterceptor implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            String methodName = method.getName();
            if (SKIP_METHODS_FOR_REDIS.contains(methodName)) {
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
                JedisInvocation ri = parseRedisInvocation(RedisMethodInfo.fromInvocation(invocation), ret);
                p.setInput(ri.args);
                p.setOutput(ri.result);
                p.setServiceCls(ri.cls);
                p.setService(ri.cls.getSimpleName());
                p.setAction(ri.method);
                MoniLogUtil.printLargeSizeLog(p, ri.maybeKey);
                String msgPrefix = "";
                if (StringUtils.isNotBlank(ri.maybeKey)) {
                    msgPrefix = "[key=" + ri.maybeKey + "]";
                }
                p.setMsgInfo(msgPrefix + p.getMsgInfo());
                MoniLogUtil.log(p);
            }
        }

        /**
         * 访问修饰符、方法名不可修改
         */
        public static RedisConnection buildProxyForRedisConnection(RedisConnection conn) {
            return ProxyUtils.getProxy(conn, new RedisConnectionFactoryInterceptor());
        }

        /**
         * 访问修饰符、方法名不可修改
         */
        public static RedisClusterConnection buildProxyForRedisClusterConnection(RedisClusterConnection conn) {
            return ProxyUtils.getProxy(conn, new RedisConnectionFactoryInterceptor());
        }

        /**
         * 访问修饰符、方法名不可修改
         */
        public static void redisRecordException(Throwable e, long cost) {
            Method m;
            try {
                m = JedisConnectionFactory.class.getDeclaredMethod("getConnection");
            } catch (Throwable ex) {
                MoniLogUtil.innerDebug("redisRecordException error", ex);
                return;
            }
            MoniLogParams p = new MoniLogParams();
            try {
                p.setCost(cost);
                p.setLogPoint(LogPoint.redis);
                p.setException(e);
                p.setSuccess(false);

                ErrorInfo errorInfo = ExceptionUtil.parseException(e);
                p.setMsgCode(errorInfo.getErrorCode());
                p.setMsgInfo(errorInfo.getErrorMsg());

                JedisInvocation ri = parseRedisInvocation(RedisMethodInfo.fromMethod(m), null);
                p.setInput(ri.args);
                p.setOutput(ri.result);
                p.setServiceCls(ri.cls);
                p.setService(ri.cls.getSimpleName());
                p.setAction(ri.method);

                String msgPrefix = "";
                if (StringUtils.isNotBlank(ri.maybeKey)) {
                    msgPrefix = "[key=" + ri.maybeKey + "]";
                }
                p.setMsgInfo(msgPrefix + p.getMsgInfo());
                MoniLogUtil.log(p);
            } catch (Throwable ex) {
                MoniLogUtil.innerDebug("redis {}.{} {} failed, {}", p.getService(), p.getAction(), p.getMsgInfo(), e.getMessage());
            }
        }
    }

    /**
     * 对RedissonClient的执行进行监控，禁止修改该方法的签名
     */
    public static Object getRedissonProxy(RedissonClient client) {
        return ProxyUtils.getProxy(client, invocation -> {
            MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
            // 判断开关
            if (moniLogProperties == null ||
                    !moniLogProperties.isComponentEnable(ComponentEnum.redis, moniLogProperties.getRedis().isEnable())) {
                return invocation.proceed();
            }
            long start = System.currentTimeMillis();
            Object result = invocation.proceed();
            boolean isTargetResult = result instanceof RMap || result instanceof RSet || result instanceof RList || result instanceof RBucket || result instanceof RBuckets || result instanceof RLock;
            if (!isTargetResult) {
                return result;
            }
            JedisInvocation ri = parseRedisInvocation(RedisMethodInfo.fromInvocation(invocation), null);
            MoniLogParams p = new MoniLogParams();
            p.setInput(ri.args);
            p.setServiceCls(ri.cls);
            p.setService(ri.cls.getSimpleName());
            p.setAction(ri.method);
            p.setInput(ri.args == null || ri.args.length == 0 ? ri.args : Arrays.stream(ri.args).filter(e -> e instanceof String).toArray());
            p.setCost(start); //取结果时再减掉此值
            p.setSuccess(true);
            p.setLogPoint(LogPoint.redis);
            p.setMsgCode(ErrorEnum.SUCCESS.name());
            p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            try {
                return ProxyUtils.getProxy(result, new RedissonResultProxy(p));
            } catch (Throwable e) {
                MoniLogUtil.innerDebug("interceptRedisson error", e);
                return result;
            }
        });
    }
    @AllArgsConstructor
    private static class RedissonResultProxy implements MethodInterceptor {
        private final MoniLogParams p;
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            String methodName = method.getName();
            if (!TARGET_REDISSON_METHODS.contains(methodName) || p == null) {
                return invocation.proceed();
            }
            Class<?> serviceCls = p.getServiceCls();
            if (serviceCls != null && serviceCls.getPackage().getName().startsWith("org.redisson")) {
                //e.g. : getBucket().set
                p.setAction(p.getAction() + "()." + methodName);
            }
            Object ret;
            try {
                ret = invocation.proceed();
                p.setOutput(ret);
                return ret;
            } catch (Throwable e) {
                Throwable ex = e;
                if (e.getMessage().contains("Unexpected exception while processing command") && e.getCause() != null) {
                    ex = e.getCause();
                }
                p.setException(ex);
                p.setSuccess(false);
                ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
                p.setMsgCode(errorInfo.getErrorCode());
                p.setMsgInfo(errorInfo.getErrorMsg());
                throw e;
            } finally {
                p.setCost(System.currentTimeMillis() - p.getCost());
                String maybeKey = chooseStringKey(p.getInput());
                MoniLogUtil.printLargeSizeLog(p, maybeKey);
                String msgPrefix = "";
                //与redisTemplate保持一致
                if (StringUtils.isNotBlank(maybeKey)) {
                    msgPrefix = "[key=" + maybeKey + "]";
                }
                p.setMsgInfo(msgPrefix + p.getMsgInfo());
                MoniLogUtil.log(p);
            }
        }
    }

    @Getter
    @AllArgsConstructor
    private static class RedisMethodInfo {
        private String cls;
        private String method;
        private Object[] args;

        static RedisMethodInfo fromInvocation(MethodInvocation inv) {
            String methodName = inv.getMethod().getName();
            String serviceName = inv.getThis().getClass().getSimpleName();
            return new RedisMethodInfo(serviceName, methodName, inv.getArguments());
        }

        static RedisMethodInfo fromMethod(Method m) {
            return new RedisMethodInfo(m.getClass().getSimpleName(), m.getName(), null);
        }
    }

    private static class JedisInvocation {
        Class<?> cls;
        String method;
        String maybeKey;
        Object[] args;
        Object result;
    }

    private static JedisInvocation parseRedisInvocation(RedisMethodInfo m, Object ret) {
        JedisInvocation ri = new JedisInvocation();
        try {
            StackTraceElement st = ThreadUtil.getNextClassFromStack(RedisMoniLogInterceptor.class);
            if (st != null) {
                ri.cls = Class.forName(st.getClassName());
                ri.method = st.getMethodName();
            } else {
                ri.cls = m.getClass();
                ri.method = m.getMethod();
            }
        } catch (Exception ignore) {
            ri.cls = m.getClass();
            ri.method = m.getMethod();
        }
        try {
            Object[] args = m.getArgs();
            ri.args = deserializeRedisArgs(args);
            ri.maybeKey = chooseStringKey(ri.args);
            ri.result = ret == null ? null : tryDeserialize(ret, false);
        } catch (Exception e) {
            MoniLogUtil.innerDebug("parseRedisInvocation-deserialize error", e);
        }
        return ri;
    }

    private static Object[] deserializeRedisArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }
        List<Object> ret = new ArrayList<>();
        boolean firstByte = true;
        for (Object arg : args) {
            if (arg instanceof RedisStringCommands.SetOption || arg instanceof RedisStringCommands.BitOperation) {
                continue;
            }
            ret.add(tryDeserialize(arg, firstByte));
            if (isByteKey(arg)) {
                if (firstByte) {
                    firstByte = false;
                }
            }
        }
        return ret.toArray(new Object[0]);
    }

    private static Object tryDeserialize(Object arg, boolean first) {
        if (!isByteKey(arg)) {
            return arg;
        }
        byte[] byted = getByteKey(arg);
        if (first) {
            return stringSerializer.deserialize(byted);
        } else {
            try {
                return jdkSerializer.deserialize(byted);
            } catch (SerializationException e) {
                return stringSerializer.deserialize(byted);
            }
        }
    }

    private static boolean isByteKey(Object arg) {
        return getByteKey(arg) != null;
    }

    private static byte[] getByteKey(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof byte[]) {
            return (byte[]) arg;
        }
        if (arg instanceof byte[][] && ((byte[][]) arg).length == 1) {
            return ((byte[][]) arg)[0];
        }
        return null;
    }

    private static String chooseStringKey(Object[] input) {
        if (input == null) {
            return null;
        }
        for (Object o : input) {
            if (o instanceof String) {
                return (String) o;
            }
        }
        return null;
    }
}