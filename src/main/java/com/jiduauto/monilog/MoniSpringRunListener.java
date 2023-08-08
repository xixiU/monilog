package com.jiduauto.monilog;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.Ordered;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yp
 * @date 2023/08/08
 */
@Slf4j
final class MoniSpringRunListener implements SpringApplicationRunListener, Ordered {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private MoniSpringRunListener(SpringApplication app, String[] args) {
        String clsName = "org.apache.http.impl.client.HttpClientBuilder";
        String body = HttpClientInterceptor.class.getCanonicalName() + ".addInterceptors(this);";
        try {
            enhanceDefaultConstructor(clsName, "()V", body);
        } catch (NotFoundException ignore) {
        } catch (Throwable e) {
            log.warn("failed to rebuild HttpClientBuilder class, {}", e.getMessage());
        }
        SpringApplicationRunListener.super.starting();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static void enhanceDefaultConstructor(String clsName, String descriptor, String srcCode) throws Throwable {
        if (INITIALIZED.get()) {
            return;
        }
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctCls = classPool.getCtClass(clsName);
        ctCls.getConstructor(descriptor).setBody(srcCode);
        ctCls.writeFile();
        Class<?> targetCls = ctCls.toClass();
        log.info("constructor of '{}' has bean enhanced...", targetCls.getCanonicalName());
        INITIALIZED.set(true);
    }

    private static void enhanceMethod(String clsName, String method, String newMethodBody) throws Throwable {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctCls = classPool.getCtClass(clsName);
        ctCls.getDeclaredMethod(method).setBody(newMethodBody);
        ctCls.writeFile();
        Class<?> targetCls = ctCls.toClass();
        log.info("method[{}] of '{}' has bean enhanced...", method, targetCls.getCanonicalName());
    }
}