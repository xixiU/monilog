package com.jiduauto.monilog;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.cache.Cache;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.util.ByteUtils;

import javax.annotation.Resource;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * @author yp
 * @date 2023/08/04
 */
@Slf4j
class MoniLogAppListener implements ApplicationListener<ApplicationPreparedEvent> {
    private static final String REDIS_TEMPLATE = "org.springframework.data.redis.core.RedisTemplate";
    private static final String REDIS_CACHE_MANAGER = "org.springframework.data.redis.cache.RedisCacheManager";

    @Resource
    private MoniLogProperties moniLogProperties;

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        ConfigurableApplicationContext ctx = event.getApplicationContext();
        if (!moniLogProperties.isComponentEnable("redis", moniLogProperties.getRedis().isEnable())) {
            return;
        }
        log.info(">>>monilog redis[jedis] start...");
        try {
            //这里仅增加redisTemplate，不包括Redisson， Redisson的比较复杂，将通过Aop实现
            enhanceRedisTemplate(ctx);
        } catch (Throwable e) {
            MoniLogUtil.innerDebug("enhanceRedisTemplate error", e);
        }
        try {
            //enhanceRedisCacheManager(ctx);
        } catch (Throwable e) {
            MoniLogUtil.innerDebug("enhanceRedisCacheManager error", e);
        }
    }

    /**
     * 对RedisTemplate的执行监控并不太容易，需要以曲线求国的方式进行
     * 此方法对RedisTemplate进行增强，只所以将此操作放在Spring的PreparedEvent之后进行，是因为在javakit环境下，
     * RedisTemplate实例是由javakit手动初始化并使用BeanFactory.registerSingleton来注册到Spring中去的，这样一来，
     * 我们没有办法在Spring的任何生命周期回调函数中拿到这个bean，也就没办法对其在实例化阶段进行增强。另一方面，我们也没有办法对
     * RedisTemplate进行AOP拦截，因为它执行Redis命令是通过RedisConnectionFactory的RedisConnection进行的，我们需要代理的是
     * RedisConnection的执行命令，而不能是RedisTemplate或RedisConnectionFactory，而RedisConnection又不是一个SpringBean
     * 所以声明式AOP的路是走不通的。 因此我们选择的方法只能是在Spring的PreparedEvent之后，通过为RedisConnectionFactory创建第一层代理，
     * 然后拦截第一层代理对象中的getConnection方法，对此方法的返回值(RedisConnection)再进行代理，得到一个ConnectionProxy，然后再对
     * ConnectionProxy的所有我们关心的redis指令进行拦截和监控 (耗时、大key等)。
     * <p>
     * 注意，为了让摘要(或详情)日志中输出的action信息更贴近业务，我们通过回溯线程栈的方式，跳过指定组件(org.springframework.*)后，
     * 找到最贴近业务调用的目标服务作为监控的目标service和action
     */

    @SuppressWarnings("all")
    private void enhanceRedisTemplate(ConfigurableApplicationContext ctx) {
        if (null == MoniLogPostProcessor.getTargetCls(REDIS_TEMPLATE)) {
            return;
        }
        Map<String, RedisTemplate> templates = ctx.getBeansOfType(RedisTemplate.class);
        if (MapUtils.isEmpty(templates)) {
            return;
        }
        for (RedisTemplate t : templates.values()) {
            RedisConnectionFactory proxy = buildProxy(t.getConnectionFactory(), t.getKeySerializer(), t.getValueSerializer(), moniLogProperties.getRedis());
            t.setConnectionFactory(proxy);
        }
    }

    private void enhanceRedisCacheManager(ConfigurableApplicationContext ctx) {
        if (null == MoniLogPostProcessor.getTargetCls(REDIS_CACHE_MANAGER)) {
            return;
        }
        RedisCacheManager rcm = SpringUtils.getBeanWithoutException(RedisCacheManager.class);
        if (rcm == null) {
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
                RedisSerializer<String> keySerializer = new CachedRedisSerializer<>(cfg.getKeySerializationPair());
                RedisSerializer<Object> valueSerializer = new CachedRedisSerializer<>(cfg.getValueSerializationPair());

                RedisCacheWriter cacheWriter = ReflectUtil.getPropValue(c, "cacheWriter");
                assert cacheWriter != null;
                RedisConnectionFactory connectionFactory = ReflectUtil.getPropValue(cacheWriter, "connectionFactory");
                assert connectionFactory != null;
                RedisConnectionFactory proxy = buildProxy(connectionFactory, keySerializer, valueSerializer, moniLogProperties.getRedis());
                ReflectUtil.setPropValue(cacheWriter, "connectionFactory", proxy, false);
            } catch (Throwable e) {
                MoniLogUtil.innerDebug("enhanceRedisCacheManager error", e);
            }
        }
    }
    private static RedisConnectionFactory buildProxy(RedisConnectionFactory origin, RedisSerializer<?> keySerializer, RedisSerializer<?> valueSerializer, MoniLogProperties.RedisProperties conf) {
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
}
