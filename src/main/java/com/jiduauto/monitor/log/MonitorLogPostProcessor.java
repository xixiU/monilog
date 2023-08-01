package com.jiduauto.monitor.log;

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
@DependsOn("monitorLogProperties")
public class MonitorLogPostProcessor implements BeanPostProcessor, PriorityOrdered {
    private MonitorLogProperties monitorLogProperties;

    @Autowired
    public MonitorLogPostProcessor(MonitorLogProperties monitorLogProperties) {
        this.monitorLogProperties = monitorLogProperties;
    }
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // feign的
        if (bean instanceof Client && needComponent("feign")) {
            return FeignMonitorInterceptor.getProxyBean(bean);
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);

    }

    @Override
    public int getOrder() {
        return  Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean needComponent(String component){
        if (monitorLogProperties == null) {
            // todo
            return false;
        }
        Set<String> componentIncludes = monitorLogProperties.getComponentIncludes();
        if (CollectionUtils.isEmpty(componentIncludes)) {
            return false;
        }
        if (componentIncludes.contains("*") || componentIncludes.contains(component)) {
            Set<String> componentExcludes = monitorLogProperties.getComponentExcludes();
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
