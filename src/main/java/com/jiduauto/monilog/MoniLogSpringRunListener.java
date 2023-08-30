package com.jiduauto.monilog;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.Ordered;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jiduauto.monilog.MoniLogPostProcessor.HTTP_ASYNC_CLIENT_BUILDER;
import static com.jiduauto.monilog.MoniLogPostProcessor.HTTP_CLIENT_BUILDER;

/**
 * @author yp
 * @date 2023/08/08
 */
@Slf4j
final class MoniLogSpringRunListener implements SpringApplicationRunListener, Ordered {
    private static final String HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER = "org.apache.http.impl.nio.client.AbstractClientExchangeHandler";
    private static final Map<String, AtomicBoolean> map = new HashMap<String, AtomicBoolean>() {{
        put(HTTP_CLIENT_BUILDER, new AtomicBoolean());
        put(HTTP_ASYNC_CLIENT_BUILDER, new AtomicBoolean());
        put(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER, new AtomicBoolean());
    }};

    private MoniLogSpringRunListener(SpringApplication app, String[] args) {
        doEnhance(HTTP_CLIENT_BUILDER, "addInterceptorsForBuilder");
        boolean success = doEnhance(HTTP_ASYNC_CLIENT_BUILDER, "addInterceptorsForAsyncBuilder");
        if (success) {
            doEnhanceAsyncErrorHandler(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER);
        }
        SpringApplicationRunListener.super.starting();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }


    private static boolean doEnhance(String clsName, String helperMethod) {
        String body = HttpClientMoniLogInterceptor.class.getCanonicalName() + "." + helperMethod + "(this);";
        try {
            enhanceDefaultConstructor(clsName, "()V", body);
            return true;
        } catch (NotFoundException ignore) {
        } catch (Throwable e) {
            log.warn("failed to rebuild {} class, {}", clsName, e.getMessage());
        }
        return false;
    }

    private static void enhanceDefaultConstructor(String clsName, String descriptor, String srcCode) throws Throwable {
        if (map.get(clsName).get()) {
            return;
        }

        ClassPool classPool = ClassPool.getDefault();
        CtClass ctCls = classPool.getCtClass(clsName);
        ctCls.getConstructor(descriptor).setBody(srcCode);
        ctCls.writeFile();
        Class<?> targetCls = ctCls.toClass();
        log.info("constructor of '{}' has bean enhanced...", targetCls.getCanonicalName());
        map.get(clsName).set(true);

    }

    private static void doEnhanceAsyncErrorHandler(String httpAsyncClientExchangeHandler) {
        String body = AsyncClientExceptionHandler.class.getCanonicalName() + ".onFailed(ex, this.localContext);";
        if (map.get(httpAsyncClientExchangeHandler).get()) {
            return;
        }
        try {
            String descriptor = "";
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(httpAsyncClientExchangeHandler);
            CtMethod method = ctCls.getMethod("failed", descriptor);
            ctCls.getConstructor(descriptor).setBody(body);
            ctCls.writeFile();
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced...", targetCls.getCanonicalName());
            map.get(httpAsyncClientExchangeHandler).set(true);
        } catch (Throwable e) {
            log.warn("failed to rebuild {} class, {}", httpAsyncClientExchangeHandler, e.getMessage());
        }
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