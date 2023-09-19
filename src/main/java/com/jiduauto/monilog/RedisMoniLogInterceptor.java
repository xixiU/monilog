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
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.util.ByteUtils;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
public class RedisMoniLogInterceptor {
    private static final Set<String> SKIP_METHODS_FOR_JEDIS = Sets.newHashSet("isPipelined", "close", "isClosed", "getNativeConnection", "isQueueing", "closePipeline");
    private static final Set<String> TARGET_REDISSON_METHODS = Sets.newHashSet("get", "getAndDelete", "getAndSet", "getAndExpire", "getAndClearExpire", "put", "putIfAbsent", "putIfExists", "randomEntries", "randomKeys", "addAndGet", "containsKey", "containsValue", "remove", "replace", "putAll", "fastPut", "fastRemove", "fastReplace", "fastPutIfAbsent", "fastPutIfExists", "readAllKeySet", "readAllValues", "readAllEntrySet", "readAllMap", "keySet", "values", "entrySet", "addAfter", "addBefore", "fastSet", "readAll", "range", "random", "removeRandom", "tryAdd", "set", "trySet", "setAndKeepTTL", "setIfAbsent", "setIfExists", "compareAndSet", "tryLock", "lock", "tryLock", "lockInterruptibly");

    @AllArgsConstructor
    static class JedisTemplateInterceptor implements MethodInterceptor {
        private final RedisSerializer<?> keySerializer;
        private final RedisSerializer<?> valueSerializer;
        private final MoniLogProperties.RedisProperties redisProperties;

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            String methodName = method.getName();
            if (SKIP_METHODS_FOR_JEDIS.contains(methodName)) {
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
                JedisInvocation ri = parseRedisInvocation(invocation, ret);
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

        static void recordException(Throwable e, MethodInvocation invocation, long cost, RedisSerializer<?> keySerializer, RedisSerializer<?> valueSerializer, MoniLogProperties.RedisProperties redis) {
            MoniLogParams p = new MoniLogParams();
            try {
                p.setCost(cost);
                p.setLogPoint(LogPoint.redis);
                p.setException(e);
                p.setSuccess(false);

                ErrorInfo errorInfo = ExceptionUtil.parseException(e);
                p.setMsgCode(errorInfo.getErrorCode());
                p.setMsgInfo(errorInfo.getErrorMsg());

                JedisInvocation ri = parseRedisInvocation(invocation, keySerializer, valueSerializer, null);
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

        private JedisInvocation parseRedisInvocation(MethodInvocation invocation, Object ret) {
            return parseRedisInvocation(invocation, keySerializer, valueSerializer, ret);
        }

        private static JedisInvocation parseRedisInvocation(MethodInvocation invocation, RedisSerializer<?> keySerializer, RedisSerializer<?> valueSerializer, Object ret) {
            JedisInvocation ri = new JedisInvocation();
            try {
                StackTraceElement st = ThreadUtil.getNextClassFromStack(null);
                if (st != null) {
                    ri.cls = Class.forName(st.getClassName());
                    ri.method = st.getMethodName();
                } else {
                    ri.cls = invocation.getThis().getClass();
                    ri.method = invocation.getMethod().getName();
                }
            } catch (Exception ignore) {
                ri.cls = invocation.getThis().getClass();
                ri.method = invocation.getMethod().getName();
            }
            try {
                Object[] args = invocation.getArguments();
                ri.args = deserializeRedisArgs(keySerializer, valueSerializer, args);
                ri.maybeKey = parseJedisMaybeKey(keySerializer, args);
                ri.result = ret == null ? null : deserializeRedisResult(valueSerializer, ret);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("parseRedisInvocation-deserialize error", e);
            }
            return ri;
        }
    }

    /**
     * 对RedissonClient的执行监控也不太容易，仍需要以曲线求国的方式进行<br>
     * RedissonClient是异步的，且在javakit环境下，其client不受spring生命周期约束，即不能在Spring处理bean的生命周期内进行拦截增强，
     * 此外，它也不能像RedisTemplate那样在Spring启动准备工作妥当后再去增强RedisConnectionFactory，因为RedissonClient内部没有可被增强的连接器，
     * 对RedissonClient的增强，目前只能用SpringAOP去实现(好在Redisson实现了一个定义清晰的接口)，但因为RedissonClient的方法都是异步的，所以即使做了
     * AOP也并不能对RedissonClient本身做任何处理，只能对其特定方法的返回进行代理，并在创建代理对象前传入已经构造了一半的MonilogParams对象。
     * 最后再拦截异步响应结果的代理对象的方法，并加以处理， 具体步骤如下：<br>
     * <ol>
     * <li> 基于普通AOP增强RedissonClient，拦截返回结果为RMap,RSet,RList,RBucket,RBuckets,RLock的所有方法：</li>
     * <li> 正常执行原方法</li>
     * <li> 对返回结果进行包装和增强,即：替换为一个代理对象，同时这个代理结果被new出来时，顺便保存执行前的时间点信息、上下文信息</li>
     * <li> 再监控此结果类的代理对象的方法执行，并统计耗时、大key等</li>
     * </ol>
     */
    @Aspect
    @AllArgsConstructor
    static class RedissonInterceptor {
        private final MoniLogProperties.RedisProperties redisProperties;

        @Around("execution(public org.redisson.api.R* org.redisson.api.RedissonClient+.*(..))")
        private Object interceptRedisson(ProceedingJoinPoint pjp) throws Throwable {
            MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
            // 判断开关
            if (moniLogProperties == null ||
                    !moniLogProperties.isComponentEnable("redis", moniLogProperties.getRedis().isEnable())) {
                return pjp.proceed();
            }
            long start = System.currentTimeMillis();
            Object result = pjp.proceed();
            boolean isTargetResult = result instanceof RMap || result instanceof RSet || result instanceof RList || result instanceof RBucket || result instanceof RBuckets || result instanceof RLock;
            if (!isTargetResult) {
                return result;
            }
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
            try {
                return ProxyUtils.getProxy(result, new RedissonResultProxy(p, redisProperties));
            } catch (Throwable e) {
                MoniLogUtil.innerDebug("interceptRedisson error", e);
                return result;
            }
        }
    }

    @AllArgsConstructor
    private static class RedissonResultProxy implements MethodInterceptor {
        private final MoniLogParams p;
        private final MoniLogProperties.RedisProperties redisProperties;

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            String methodName = method.getName();
            if (!TARGET_REDISSON_METHODS.contains(methodName) || p == null) {
                return invocation.proceed();
            }
            //e.g. : getBucket().set
            p.setAction(p.getAction() + "()." + methodName);
            Object ret = null;
            try {
                ret = invocation.proceed();
                p.setOutput(ret);
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
                String maybeKey = parserRedissonMaybeKey(p.getInput());
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

    private static class JedisInvocation {
        Class<?> cls;
        String method;
        String maybeKey;
        Object[] args;
        Object result;
    }

    public static void enhanceRedisCacheManager(RedisCacheManager rcm) {
        if (null == rcm) {
            return;
        }
        MoniLogProperties prop = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        if (prop == null || prop.getRedis() == null) {
            return;
        }
        MoniLogProperties.RedisProperties conf = prop.getRedis();
        if (!prop.isComponentEnable("redis", conf.isEnable())) {
            return;
        }
        for (String n : rcm.getCacheNames()) {
            Cache cache = rcm.getCache(n);
            if (!(cache instanceof RedisCache)) {
                continue;
            }
            try {
                RedisCache c = (RedisCache) cache;
                RedisCacheConfiguration cfg = c.getCacheConfiguration();
                RedisSerializer<String> keySerializer = new RedisMoniLogInterceptor.CachedRedisSerializer<>(cfg.getKeySerializationPair());
                RedisSerializer<Object> valueSerializer = new RedisMoniLogInterceptor.CachedRedisSerializer<>(cfg.getValueSerializationPair());

                RedisCacheWriter cacheWriter = ReflectUtil.getPropValue(c, "cacheWriter");
                assert cacheWriter != null;
                RedisConnectionFactory connectionFactory = ReflectUtil.getPropValue(cacheWriter, "connectionFactory");
                assert connectionFactory != null;
                RedisConnectionFactory proxy = RedisMoniLogInterceptor.buildProxy(connectionFactory, keySerializer, valueSerializer, conf);
                ReflectUtil.setPropValue(cacheWriter, "connectionFactory", proxy, false);
            } catch (Throwable e) {
                MoniLogUtil.innerDebug("enhanceRedisCacheManager error", e);
            }
        }
    }

    static RedisConnectionFactory buildProxy(RedisConnectionFactory origin, RedisSerializer<?> keySerializer, RedisSerializer<?> valueSerializer, MoniLogProperties.RedisProperties conf) {
        return ProxyUtils.getProxy(origin, invocation -> {
            String methodName = invocation.getMethod().getName();
            if (!methodName.equals("getConnection")) {
                return invocation.proceed();
            }
            long start = System.currentTimeMillis();
            Object conn;
            try {
                conn = invocation.proceed();
            } catch (Throwable e) {
                RedisMoniLogInterceptor.JedisTemplateInterceptor.recordException(e, invocation, System.currentTimeMillis() - start, keySerializer, valueSerializer, conf);
                throw e;
            }
            return ProxyUtils.getProxy(conn, new RedisMoniLogInterceptor.JedisTemplateInterceptor(keySerializer, valueSerializer, conf));
        });
    }

    @AllArgsConstructor
    private static class CachedRedisSerializer<T> implements RedisSerializer<T> {
        private final RedisSerializationContext.SerializationPair<T> serializationPair;

        @Override
        public byte[] serialize(T t) throws SerializationException {
            return t == null ? null : ByteUtils.getBytes(serializationPair.write(t));
        }

        @Override
        public T deserialize(byte[] bytes) throws SerializationException {
            return bytes == null ? null : serializationPair.read(ByteBuffer.wrap(bytes));
        }
    }

    private static Object[] deserializeRedisArgs(RedisSerializer<?> keySerializer, RedisSerializer<?> valueSerializer, Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }
        List<Object> ret = new ArrayList<>();
        boolean firstByte = true;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof byte[]) {
                if (firstByte) {
                    ret.add(keySerializer.deserialize((byte[]) arg));
                    firstByte = false;
                } else {
                    ret.add(valueSerializer.deserialize((byte[]) arg));
                }
            } else if (arg instanceof RedisStringCommands.SetOption || arg instanceof RedisStringCommands.BitOperation) {
                continue;
            } else {
                ret.add(arg);
            }
        }
        return ret.toArray(new Object[0]);
    }

    private static Object deserializeRedisResult(RedisSerializer<?> valueSerializer, Object ret) {
        return valueSerializer == null || !(ret instanceof byte[]) ? ret : valueSerializer.deserialize((byte[]) ret);
    }

    private static String parseJedisMaybeKey(RedisSerializer<?> serializer, Object[] args) {
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

    private static String parserRedissonMaybeKey(Object[] input) {
        if (input == null || input.length == 0 || !(input[0] instanceof String)) {
            return null;
        }
        return (String) input[0];
    }
}