package com.jiduauto.monilog;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yp
 * @date 2023/07/30
 */
class ProxyUtils {
    /**
     * 创建指定对象的代理类
     *
     * @param obj         对象
     * @param interceptor 代理方法
     * @return 代理类
     */
    @SuppressWarnings("unchecked")
    static <T> T getProxy(T obj, MethodInterceptor interceptor) {
        ProxyFactory proxy = new ProxyFactory(obj);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (T) proxy.getProxy();
    }
    static <T extends Annotation> T copyAnnotation(T anno) {
        return copyAnnotation(anno, null);
    }

    @SuppressWarnings("unchecked")
    static <T extends Annotation> T copyAnnotation(T origin, Map<String, Object> specifiedValues) {
        final String memberValuesFieldName = "memberValues";
        try {
            Class<?> cls = origin.getClass();
            if (Proxy.isProxyClass(cls)) {
                cls = origin.annotationType();
            }
            InvocationHandler handler = Proxy.getInvocationHandler(origin);
            Field memberValuesField = handler.getClass().getDeclaredField(memberValuesFieldName);
            memberValuesField.setAccessible(true);
            Map<String, Object> oldValues = (Map<String, Object>) memberValuesField.get(handler);

            Constructor<?> constructor = Class.forName("sun.reflect.annotation.AnnotationInvocationHandler").getDeclaredConstructor(Class.class, Map.class);
            constructor.setAccessible(true);
            InvocationHandler proxyHandler = (InvocationHandler) constructor.newInstance(cls, new HashMap<>(oldValues));
            Annotation proxy = (Annotation) Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, proxyHandler);
            if (specifiedValues != null) {
                InvocationHandler newHandler = Proxy.getInvocationHandler(proxy);
                Field newField = newHandler.getClass().getDeclaredField(memberValuesFieldName);
                newField.setAccessible(true);
                Map<String, Object> newValues = (Map<String, Object>) newField.get(newHandler);
                for (Map.Entry<String, Object> me : specifiedValues.entrySet()) {
                    if (me.getKey() != null && me.getValue() != null) {
                        newValues.put(me.getKey(), me.getValue());
                    }
                }
            }
            return (T) proxy;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
