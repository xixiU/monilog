package com.jiduauto.log.feign;

import feign.Client;
import feign.Feign;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
@ConditionalOnExpression("('${monitor.log.endpoints.include:*}'.equals('*') or '${monitor.log.endpoints.include}'.contains('feign')) and !('${monitor.log.endpoints.exclude:}'.equals('*') or '${monitor.log.endpoints.exclude:}'.contains('feign'))")
@ConditionalOnClass(Feign.class)
@Slf4j
class FeignMonitorLogConfiguration {
    @Value("${monitor.log.feign.bool.expr.default:$.code==0,$.code==200}")
    private String defaultBoolExpr;

    @Bean
    public FeignClientEnhanceProcessor feignClientEnhanceProcessor() {
        return new FeignClientEnhanceProcessor(defaultBoolExpr);
    }

    @AllArgsConstructor
    static class FeignClientEnhanceProcessor implements BeanPostProcessor, Ordered {
        private final String defaultBoolExpr;

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof Client && !(bean instanceof EnhancedFeignClient)) {
                return new EnhancedFeignClient((Client) bean, defaultBoolExpr);
            }
            return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
        }

        @Override
        public int getOrder() {
            return Integer.MAX_VALUE;
        }
    }
}
