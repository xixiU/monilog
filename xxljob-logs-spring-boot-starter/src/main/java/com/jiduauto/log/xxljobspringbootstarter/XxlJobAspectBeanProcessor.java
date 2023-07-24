package com.jiduauto.log.xxljobspringbootstarter;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * @author fan.zhang02
 * @date 2023/07/24/09:24
 */
public class XxlJobAspectBeanProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        XxlJob annotation = AnnotationUtils.findAnnotation(clazz, XxlJob.class);

        if (annotation == null) {
            return bean;
        }

        //生成动态代理
        return bean;
    }
}
