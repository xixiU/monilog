package com.jiduauto.monitor.log;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
class RedisLogMonitorInterceptor {
    static class RedisTemplateEnhanceProcessor implements BeanPostProcessor, Ordered {
        private final MonitorLogProperties.RedisProperties redisProperties;

        public RedisTemplateEnhanceProcessor(MonitorLogProperties.RedisProperties redisProperties) {
            this.redisProperties = redisProperties;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof RedisTemplate && !(bean instanceof RedisLogMonitorInterceptor.EnhancedRedisTemplate)) {
                return new RedisLogMonitorInterceptor.EnhancedRedisTemplate<>((RedisTemplate<?, ?>) bean, redisProperties);
            }
            return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
        }

        @Override
        public int getOrder() {
            return Integer.MAX_VALUE;
        }
    }

    @AllArgsConstructor
    private static class EnhancedRedisTemplate<K, V> extends RedisTemplate<K, V> {
        private final RedisTemplate<K, V> redisTemplate;
        private final MonitorLogProperties.RedisProperties redisProperties;

        @Override
        public <T> T execute(RedisCallback<T> action, boolean exposeConnection, boolean pipeline) {
            long start = System.currentTimeMillis();
            long cost = 0;
            Throwable ex = null;
            T ret = null;
            try {
                ret = redisTemplate.execute(action, exposeConnection, pipeline);
            } catch (Throwable e) {
                ex = e;
            } finally {
                cost = System.currentTimeMillis() - start;
            }
            log.info("redis monitor execute... to be implemented");

            MonitorLogParams p = new MonitorLogParams();
            p.setServiceCls(null);
            p.setService("");
            p.setAction("");
            p.setTags(new String[]{});

            p.setCost(cost);
            p.setException(ex);
            p.setSuccess(true);
            p.setLogPoint(LogPoint.redis);
            p.setInput(new Object[]{});
            p.setMsgCode(ErrorEnum.SUCCESS.name());
            p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            if (ex != null) {
                ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
                if (errorInfo != null) {
                    p.setMsgCode(errorInfo.getErrorCode());
                    p.setMsgInfo(errorInfo.getErrorMsg());
                }
            }
            //after
            return ret;
        }
    }
}