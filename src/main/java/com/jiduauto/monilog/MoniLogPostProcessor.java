package com.jiduauto.monilog;

import feign.Client;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import java.util.Set;


@Component
@DependsOn("moniLogProperties")
public class MoniLogPostProcessor implements BeanPostProcessor, PriorityOrdered {
    private MoniLogProperties moniLogProperties;

    @Autowired
    public MoniLogPostProcessor(MoniLogProperties moniLogProperties) {
        this.moniLogProperties = moniLogProperties;
    }
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // feign的
        if (bean instanceof Client && needComponent("feign")) {
            return FeignMoniLogInterceptor.getProxyBean(bean);
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);

    }

    @Override
    public int getOrder() {
        return  Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean needComponent(String component){
        if (moniLogProperties == null) {
            // todo
            return false;
        }
        Set<String> componentIncludes = moniLogProperties.getComponentIncludes();
        if (CollectionUtils.isEmpty(componentIncludes)) {
            return false;
        }
        if (componentIncludes.contains("*") || componentIncludes.contains(component)) {
            Set<String> componentExcludes = moniLogProperties.getComponentExcludes();
            if (CollectionUtils.isEmpty(componentExcludes)) {
                return true;
            }
            if (componentExcludes.contains("*") || componentExcludes.contains(component)) {
                return false;
            }
            return true;
        }
        return false;
    }
}
