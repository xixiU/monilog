package com.jiduauto.monilog;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.Ordered;

/**
 * @author yp
 * @date 2023/08/08
 */
@Slf4j
final class MoniLogSpringRunListener implements SpringApplicationRunListener, Ordered {
    private MoniLogSpringRunListener(SpringApplication app, String[] args) {
        doEnhance(MoniLogPostProcessor.HTTP_CLIENT_BUILDER, "addInterceptorsForBuilder");
        doEnhance(MoniLogPostProcessor.HTTP_ASYNC_CLIENT_BUILDER, "addInterceptorsForAsyncBuilder");
        SpringApplicationRunListener.super.starting();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }


    private static void doEnhance(String clsName, String helperMethod) {
        String body = HttpClientMoniLogInterceptor.class.getCanonicalName() + "." + helperMethod + "(this);";
        try {
            enhanceDefaultConstructor(clsName, "()V", body);
        } catch (NotFoundException ignore) {
        } catch (Throwable e) {
            log.warn("failed to rebuild {} class, {}", clsName, e.getMessage());
        }
    }

    private static void enhanceDefaultConstructor(String clsName, String descriptor, String srcCode) throws Throwable {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctCls = classPool.getCtClass(clsName);
        ctCls.getConstructor(descriptor).setBody(srcCode);
        ctCls.writeFile();
        Class<?> targetCls = ctCls.toClass();
        log.info("constructor of '{}' has bean enhanced...", targetCls.getCanonicalName());
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