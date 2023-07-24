package com.jiduauto.log.feign;

import feign.Client;
import feign.Feign;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * @author yp
 * @date 2023/07/24
 */
@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "monitor.log.feign", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(Feign.class)
@Slf4j
public class FeignMonitorLogConfiguration {

    @Bean
    public FeignClientEnhanceProcessor feignClientEnhanceProcessor() {
        return new FeignClientEnhanceProcessor();
    }

    static class FeignClientEnhanceProcessor implements BeanPostProcessor, Ordered {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof Client && !(bean instanceof EnhancedFeignClient)) {
                return new EnhancedFeignClient((Client) bean);
            }
            return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
        }

        @Override
        public int getOrder() {
            return Integer.MAX_VALUE;
        }
    }
}
