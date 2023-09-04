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
import static com.jiduauto.monilog.MoniLogUtil.INNER_DEBUG_PREFIX;

/**
 * @author yp
 * @date 2023/08/08
 */
@Slf4j
final class MonilogEnhancer implements SpringApplicationRunListener, Ordered {
    private static final String HTTP_SYNC_CLIENT = "org.apache.http.impl.client.CloseableHttpClient";
    private static final String HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER = "org.apache.http.impl.nio.client.AbstractClientExchangeHandler";

    private static final String FEIGN_CLIENT = "feign.Client";

    private static final Map<String, AtomicBoolean> FLAGS = new HashMap<String, AtomicBoolean>() {{
        put(HTTP_CLIENT_BUILDER, new AtomicBoolean());
        put(HTTP_SYNC_CLIENT, new AtomicBoolean());
        put(HTTP_ASYNC_CLIENT_BUILDER, new AtomicBoolean());
        put(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER, new AtomicBoolean());
        put(FEIGN_CLIENT, new AtomicBoolean());
    }};

    private MonilogEnhancer(SpringApplication app, String[] args) {
        boolean success = doEnhance(HTTP_CLIENT_BUILDER, "addInterceptorsForBuilder");
        if (success) {
            doEnhanceSyncErrorHandler(HTTP_SYNC_CLIENT);
        }
        success = doEnhance(HTTP_ASYNC_CLIENT_BUILDER, "addInterceptorsForAsyncBuilder");
        if (success) {
            doEnhanceAsyncErrorHandler(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER);
        }

        doEnhanceFeignClient();
        SpringApplicationRunListener.super.starting();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }


    private static boolean doEnhance(String clsName, String helperMethod) {
        if (FLAGS.get(clsName).get()) {
            return true;
        }
        try {
            String body = HttpClientMoniLogInterceptor.class.getCanonicalName() + "." + helperMethod + "(this);";
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(clsName);
            ctCls.getConstructor("()V").setBody(body);
//            ctCls.writeFile();
            Class<?> targetCls = ctCls.toClass();
            log.info("constructor of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(clsName).set(true);
            return true;
        } catch (Throwable e) {
            log.warn(INNER_DEBUG_PREFIX + "failed to rebuild [{}], {}", clsName, e.getMessage());
        }
        return false;
    }

    private static void doEnhanceSyncErrorHandler(String clsName) {
        if (FLAGS.get(clsName).get()) {
            return;
        }
        String newMethod = "private org.apache.http.client.methods.CloseableHttpResponse _doExecute(" +
                "org.apache.http.HttpHost target," +
                "org.apache.http.HttpRequest request," +
                "org.apache.http.protocol.HttpContext context)" +
                "throws java.io.IOException,org.apache.http.client.ClientProtocolException{" +
                "if(context==null)context=new org.apache.http.protocol.BasicHttpContext();" +
                "try {return doExecute(target,request,context);} catch(Throwable e){" +
                HttpClientMoniLogInterceptor.class.getCanonicalName() +
                ".onFailed(e, context);throw e;}}";
        String desc1 = "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/client/methods/CloseableHttpResponse;";
        String desc2 = "(Lorg/apache/http/client/methods/HttpUriRequest;Lorg/apache/http/protocol/HttpContext;)Lorg/apache/http/client/methods/CloseableHttpResponse;";
        String desc3 = "(Lorg/apache/http/HttpHost;Lorg/apache/http/HttpRequest;)Lorg/apache/http/client/methods/CloseableHttpResponse;";
        String body1 = "{return _doExecute($1, $2, $3);}";
        String body2 = "{org.apache.http.util.Args.notNull($1, \"HTTP request\");return _doExecute(determineTarget($1), $1, $2);}";
        String body3 = "{return _doExecute($1, $2, null);}";
        try {
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(clsName);
            CtMethod newCtm = CtNewMethod.make(newMethod, ctCls);
            ctCls.addMethod(newCtm);

            ctCls.getMethod("execute", desc1).setBody(body1);
            ctCls.getMethod("execute", desc2).setBody(body2);
            ctCls.getMethod("execute", desc3).setBody(body3);

//            ctCls.writeFile();
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(clsName).set(true);
        } catch (Throwable e) {
            log.warn(INNER_DEBUG_PREFIX + "failed to rebuild [{}], {}", clsName, e.getMessage());
        }
    }

    private static void doEnhanceAsyncErrorHandler(String cls) {
        if (FLAGS.get(cls).get()) {
            return;
        }
        String body = "{if(this.closed.compareAndSet(false, true)){" +
                HttpClientMoniLogInterceptor.class.getCanonicalName() +
                ".onFailed($1, this.localContext);" +
                "try {this.executionFailed($1);} finally " +
                "{this.discardConnection();this.releaseResources();}}}";
        try {
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(cls);
            CtMethod method = ctCls.getMethod("failed", "(Ljava/lang/Exception;)V");
            method.setBody(body);
//            ctCls.writeFile();
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(cls).set(true);
        } catch (Throwable e) {
            log.warn(INNER_DEBUG_PREFIX + "failed to rebuild [{}], {}", cls, e.getMessage());
        }
    }

    private static void doEnhanceFeignClient() {
        if (FLAGS.get(FEIGN_CLIENT).get()) {
            return;
        }
        String newMethod = "{" +
                "Throwable bizException = null;" +
                "feign.Response response= null;" +
                "long startTime = System.currentTimeMillis();" +
                "try{" +
                "java.net.HttpURLConnection connection = this.convertAndSend($1, $2);" +
                "response = this.convertResponse(connection, $1);" +
                "}catch(Throwable e){" +
                "      bizException = e;" +
                "}finally{" +
                "long endTime = System.currentTimeMillis();" +
                "long cost = endTime-startTime;"+
                FeignMoniLogInterceptor.class.getCanonicalName() + ".doFeignInvocation($1, response, cost, bizException);" +
                "}" +
                "return response;}";
        try {
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(FEIGN_CLIENT);
            CtClass[] nestedClasses = ctCls.getNestedClasses();
            nestedClasses[1].getDeclaredMethod("execute").setBody(newMethod);
//            nestedClasses[1].writeFile();
            Class<?> targetCls = nestedClasses[1].toClass();

            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(FEIGN_CLIENT).set(true);
        } catch (Throwable e) {
            log.warn(INNER_DEBUG_PREFIX + "failed to rebuild [{}], {}", FEIGN_CLIENT, e.getMessage());
        }
    }
}