package com.jiduauto.monilog;

import com.google.common.collect.Sets;
import com.xxl.job.core.handler.IJobHandler;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * spring bean的实例化过程参考：<a href="https://blog.csdn.net/m0_37588577/article/details/127639584">...</a>
 */
@Slf4j
class MoniLogPostProcessor implements BeanPostProcessor, PriorityOrdered {
    static final Map<String, Class<?>> CACHED_CLASS = new HashMap<>();
    private static final String XXL_JOB = "com.xxl.job.core.handler.IJobHandler";
    private final MoniLogProperties moniLogProperties;

    MoniLogPostProcessor(MoniLogProperties moniLogProperties) {
        this.moniLogProperties = moniLogProperties;
        loadClass();
        log.info(">>>MoniLogPostProcessor initializing...");
    }

    @Override
    public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
        if (!moniLogProperties.isEnable()) {
            return bean;
        }
        if (isTargetBean(bean, XXL_JOB)) {
            if (isComponentEnable(ComponentEnum.xxljob, moniLogProperties.getXxljob().isEnable())) {
                return XxlJobMoniLogInterceptor.getProxyBean((IJobHandler) bean);
            }
        }
        return bean;
    }


    private static boolean isTargetBean(@NotNull Object bean, String className) {
        try {
            Class<?> cls = getTargetCls(className);
            return cls != null && cls.isAssignableFrom(bean.getClass());
        } catch (Exception e) {
            return false;
        }
    }

    static Class<?> getTargetCls(String className) {
        Class<?> cls = CACHED_CLASS.get(className);
        if (cls != null) {
            return cls;
        }
        try {
            cls = Class.forName(className);
            CACHED_CLASS.put(className, cls);
            return cls;
        } catch (Exception e) {
            return null;
        }
    }


    // 校验是否排除
    private boolean isComponentEnable(ComponentEnum component, boolean componentEnable) {
        return moniLogProperties.isComponentEnable(component, componentEnable);
    }

    private static void loadClass() {
        Set<String> clsNames = Sets.newHashSet(XXL_JOB);
        for (String clsName : clsNames) {
            try {
                Class<?> cls = Class.forName(clsName);
                CACHED_CLASS.put(clsName, cls);
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
