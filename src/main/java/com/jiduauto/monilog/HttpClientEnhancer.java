package com.jiduauto.monilog;

import javassist.*;
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
final class HttpClientEnhancer implements SpringApplicationRunListener, Ordered {
    private static final String HTTP_SYNC_CLIENT = "org.apache.http.impl.client.CloseableHttpClient";
    private static final String HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER = "org.apache.http.impl.nio.client.AbstractClientExchangeHandler";
    private static final Map<String, AtomicBoolean> map = new HashMap<String, AtomicBoolean>() {{
        put(HTTP_CLIENT_BUILDER, new AtomicBoolean());
        put(HTTP_SYNC_CLIENT, new AtomicBoolean());
        put(HTTP_ASYNC_CLIENT_BUILDER, new AtomicBoolean());
        put(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER, new AtomicBoolean());
    }};

    private HttpClientEnhancer(SpringApplication app, String[] args) {
        boolean success = doEnhance(HTTP_CLIENT_BUILDER, "addInterceptorsForBuilder");
        if (success) {
            doEnhanceSyncErrorHandler(HTTP_SYNC_CLIENT);
        }
        success = doEnhance(HTTP_ASYNC_CLIENT_BUILDER, "addInterceptorsForAsyncBuilder");
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

    private static void doEnhanceSyncErrorHandler(String httpSyncClient) {
        if (map.get(httpSyncClient).get()) {
            return;
        }
        //新增一个私有方法
        String newMethod = "private org.apache.http.client.methods.CloseableHttpResponse _doExecute(" +
                "org.apache.http.HttpHost target," +
                "org.apache.http.HttpRequest request," +
                "org.apache.http.protocol.HttpContext context)" +
                "throws java.io.IOException,org.apache.http.client.ClientProtocolException{" +
                "try {return doExecute(target,request,context);} catch(Throwable e){" +
                HttpClientMoniLogInterceptor.class.getCanonicalName() +
                ".onSyncFailed(e, context);throw e;}}";
        //修改三个方法
        String desc1 = "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/client/methods/CloseableHttpResponse;";
        String desc2 = "(Lorg/apache/http/client/methods/HttpUriRequest;Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/client/methods/CloseableHttpResponse;";
        String desc3 = "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;)Lorg/apache/http/client/methods/CloseableHttpResponse;";
        String body1 = "{return _doExecute($1, $2, $3);}";
        String body2 = "{org.apache.http.util.Args.notNull($1, \"HTTP request\");return _doExecute(determineTarget($1), $1, $2);}";
        String body3 = "{return _doExecute($1, $2, null);}";
        try {
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(httpSyncClient);
            CtMethod newCtm = CtNewMethod.make(newMethod, ctCls);
            ctCls.addMethod(newCtm);

            ctCls.getMethod("execute", desc1).setBody(body1);
            ctCls.getMethod("execute", desc2).setBody(body2);
            ctCls.getMethod("execute", desc3).setBody(body3);

            ctCls.writeFile();
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced...", targetCls.getCanonicalName());
            map.get(httpSyncClient).set(true);
        } catch (Throwable e) {
            log.warn("failed to rebuild {} class, {}", httpSyncClient, e.getMessage());
        }
    }

    private static void doEnhanceAsyncErrorHandler(String httpAsyncClientExchangeHandler) {
        if (map.get(httpAsyncClientExchangeHandler).get()) {
            return;
        }
        String body = "{if(this.closed.compareAndSet(false, true)){" +
                HttpClientMoniLogInterceptor.class.getCanonicalName() +
                ".onAsyncFailed($1, this.localContext);" +
                "try {this.executionFailed($1);} finally " +
                "{this.discardConnection();this.releaseResources();}}}";
        try {
            String descriptor = "(Ljava/lang/Exception;)V";
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(httpAsyncClientExchangeHandler);
            CtMethod method = ctCls.getMethod("failed", descriptor);
            method.setBody(body);
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