package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.Map;

import static com.jiduauto.monilog.MoniLogPostProcessor.REDIS_TEMPLATE;

/**
 * @author yp
 * @date 2023/08/04
 */
@Slf4j
class MoniLogAppListener implements ApplicationListener<ApplicationPreparedEvent> {
    @Resource
    private MoniLogProperties moniLogProperties;

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        ConfigurableApplicationContext ctx = event.getApplicationContext();
        if (null == MoniLogPostProcessor.getTargetCls(REDIS_TEMPLATE)) {
            return;
        }
        Map<String, RedisTemplate> templates = ctx.getBeansOfType(RedisTemplate.class);
        if (MapUtils.isEmpty(templates)) {
            return;
        }
        if (!moniLogProperties.isComponentEnable("redis", moniLogProperties.getRedis().isEnable())) {
            return;
        }
        for (Map.Entry<String, RedisTemplate> me : templates.entrySet()) {
            String beanName = me.getKey();
            RedisTemplate template = me.getValue();
            //如果redisConnectionFactory调用的是getConnection方法，则该方法返回的结果就是一个RedisConnection对象
            //此时，我们把RedisConnection对象进行增强，让它在执行redis命令时，记录我们的监控数据
            RedisConnectionFactory proxy = ProxyUtils.getProxy(template.getConnectionFactory(), invocation -> {
                Object redisConn = invocation.proceed();
                String methodName = invocation.getMethod().getName();
                if (methodName.equals("getConnection")) {
                    log.info(">>>monilog redis [{}] start...", beanName);
                    return ProxyUtils.getProxy(redisConn, new RedisMoniLogInterceptor(template.getKeySerializer(), template.getValueSerializer(), moniLogProperties.getRedis()));
                }
                return redisConn;
            });
            template.setConnectionFactory(proxy);
        }
    }
}
