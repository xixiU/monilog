package com.jiduauto.monilog;

import javassist.ClassPool;
import javassist.CtClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.Ordered;

/**
 * @author yp
 * @date 2023/08/08
 */
@Slf4j
public class MoniSpringRunListener implements SpringApplicationRunListener, Ordered {
    @Override
    public void starting() {
        String clsName = "org.apache.http.impl.client.HttpClientBuilder";
        String body = "this.addInterceptorFirst(new com.jiduauto.monilog.MoniHttpClientBuilder.RequestInterceptor())" +
                ".addInterceptorLast(new com.jiduauto.monilog.MoniHttpClientBuilder.ResponseInterceptor());";
        try {
            enhanceDefaultConstructor(clsName, "(Ljava/lang/String;)V", body);
        } catch (Throwable e) {
            log.warn("failed to rebuild HttpClientBuilder class, {}", e.getMessage());
        }
        SpringApplicationRunListener.super.starting();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static void enhanceDefaultConstructor(String clsName, String descriptor, String srcCode) {
        try {
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(clsName);
            ctCls.getConstructor(descriptor).setBody(srcCode);
            ctCls.writeFile();
            Class<?> targetCls = ctCls.toClass();
            log.info("constructor of '{}' has bean enhanced...", targetCls.getCanonicalName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void enhanceMethod(String clsName, String method, String newMethodBody) {
        try {
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(clsName);
            ctCls.getDeclaredMethod(method).setBody(newMethodBody);
            ctCls.writeFile();
            Class<?> targetCls = ctCls.toClass();
            log.info("method[{}] of '{}' has bean enhanced...", method, targetCls.getCanonicalName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}