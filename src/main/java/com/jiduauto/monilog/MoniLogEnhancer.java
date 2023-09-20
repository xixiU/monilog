package com.jiduauto.monilog;

import javassist.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.Ordered;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jiduauto.monilog.MoniLogUtil.INNER_DEBUG_PREFIX;

/**
 * @author yp
 * @date 2023/08/08
 */
@Slf4j
final class MoniLogEnhancer implements SpringApplicationRunListener, Ordered {
    private static final String HTTP_SYNC_CLIENT = "org.apache.http.impl.client.CloseableHttpClient";
    private static final String HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER = "org.apache.http.impl.nio.client.AbstractClientExchangeHandler";

    private static final String HTTP_ASYNC_CLIENT_BUILDER = "org.apache.http.impl.nio.client.HttpAsyncClientBuilder";

    private static final String HTTP_CLIENT_BUILDER = "org.apache.http.impl.client.HttpClientBuilder";

    private static final String FEIGN_CLIENT = "feign.Client";

    private static final String ROCKET_MQ_CONSUMER = "org.apache.rocketmq.client.consumer.DefaultMQPushConsumer";
    private static final String ROCKET_MQ_PRODUCER = "org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl";

    private static final String JEDIS_CONN_FACTORY = "org.springframework.data.redis.connection.jedis.JedisConnectionFactory";
    private static final String LETTUCE_CONN_FACTORY = "org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory";

    private static final Map<String, AtomicBoolean> FLAGS = new HashMap<String, AtomicBoolean>() {{
        put(HTTP_CLIENT_BUILDER, new AtomicBoolean());
        put(HTTP_SYNC_CLIENT, new AtomicBoolean());
        put(HTTP_ASYNC_CLIENT_BUILDER, new AtomicBoolean());
        put(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER, new AtomicBoolean());
        put(FEIGN_CLIENT, new AtomicBoolean());
        put(ROCKET_MQ_CONSUMER, new AtomicBoolean());
        put(ROCKET_MQ_PRODUCER, new AtomicBoolean());
        put(JEDIS_CONN_FACTORY, new AtomicBoolean());
        put(LETTUCE_CONN_FACTORY, new AtomicBoolean());
    }};

    private MoniLogEnhancer(SpringApplication app, String[] args) {
        enhanceHttpClient();
        enhanceFeignClient();
        enhanceRocketMqConsumer();
        enhanceRocketProducer();
        enhanceRedisConnFactory();
        SpringApplicationRunListener.super.starting();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static void enhanceHttpClient() {
        boolean success = doEnhanceHttp(HTTP_CLIENT_BUILDER, "addInterceptorsForBuilder");
        if (success) {
            doEnhanceSyncErrorHandler();
        }
        success = doEnhanceHttp(HTTP_ASYNC_CLIENT_BUILDER, "addInterceptorsForAsyncBuilder");
        if (success) {
            doEnhanceAsyncErrorHandler();
        }
    }

    private static void enhanceFeignClient() {
        if (FLAGS.get(FEIGN_CLIENT).get()) {
            return;
        }
        String newMethod = "{Throwable ex = null; feign.Response ret = null; long startTime = System.currentTimeMillis();" +
                "try {ret = this.convertResponse(this.convertAndSend($1, $2), $1);} catch(Throwable e){ex = e;} finally {" +
                "ret=" + FeignMoniLogInterceptor.class.getCanonicalName() + ".doRecord($1, ret, System.currentTimeMillis()-startTime, ex);" +
                "if (ex != null) {throw ex;}}return ret;}";
        try {
            CtClass ctCls = getCtClass(FEIGN_CLIENT);
            CtClass[] nestedClasses = ctCls.getNestedClasses();
            Arrays.sort(nestedClasses, Comparator.comparing(CtClass::getName));
            // 里面有两个内部类，第一个是Default，第二个是Proxy
            nestedClasses[0].getDeclaredMethod("execute").setBody(newMethod);
            Class<?> targetCls = nestedClasses[0].toClass();

            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(FEIGN_CLIENT).set(true);
        } catch (Throwable e) {
            logWarn(e, FEIGN_CLIENT);
        }
    }


    private static void enhanceRocketMqConsumer() {
        if (FLAGS.get(ROCKET_MQ_CONSUMER).get()) {
            return;
        }
        String interceptorCls = RocketMqMoniLogInterceptor.class.getCanonicalName();
        String enhancedBody = "{if ($1 instanceof org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently){this.messageListener = new " + interceptorCls +
                ".EnhancedListenerConcurrently((org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently) $1);" +
                "} else if ($1 instanceof org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly){" +
                "this.messageListener = new " + interceptorCls +
                ".EnhancedListenerOrderly((org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly) $1);" +
                "} else {this.messageListener = $1;}}";
        String desc = "(Lorg/apache/rocketmq/client/consumer/listener/MessageListener;)V";
        try {
            CtClass ctCls = getCtClass(ROCKET_MQ_CONSUMER);
            ctCls.getMethod("setMessageListener", desc).setBody(enhancedBody);
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(ROCKET_MQ_CONSUMER).set(true);
        } catch (Throwable e) {
            logWarn(e, ROCKET_MQ_CONSUMER);
        }
    }

    private static void enhanceRocketProducer() {
        if (FLAGS.get(ROCKET_MQ_PRODUCER).get()) {
            return;
        }
        String enhancedBody = "{this.start(true);this.registerSendMessageHook(new " +
                RocketMqMoniLogInterceptor.class.getCanonicalName() + ".RocketMQProducerEnhanceProcessor());}";
        try {
            CtClass ctCls = getCtClass(ROCKET_MQ_PRODUCER);
            ctCls.getMethod("start", "()V").setBody(enhancedBody);
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(ROCKET_MQ_PRODUCER).set(true);
        } catch (Throwable e) {
            logWarn(e, ROCKET_MQ_PRODUCER);
        }
    }

    private static CtClass getCtClass(String clsName) throws NotFoundException {
        ClassPool classPool = ClassPool.getDefault();
        classPool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));
        return classPool.getCtClass(clsName);
    }

    private static boolean doEnhanceHttp(String clsName, String helperMethod) {
        if (FLAGS.get(clsName).get()) {
            return true;
        }
        try {
            String body = HttpClientMoniLogInterceptor.class.getCanonicalName() + "." + helperMethod + "(this);";
            CtClass ctCls = getCtClass(clsName);
            ctCls.getConstructor("()V").setBody(body);
            Class<?> targetCls = ctCls.toClass();
            log.info("constructor of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(clsName).set(true);
            return true;
        } catch (Throwable e) {
            logWarn(e, clsName);
        }
        return false;
    }

    private static void doEnhanceSyncErrorHandler() {
        if (FLAGS.get(HTTP_SYNC_CLIENT).get()) {
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
            CtClass ctCls = getCtClass(HTTP_SYNC_CLIENT);
            CtMethod newCtm = CtNewMethod.make(newMethod, ctCls);
            ctCls.addMethod(newCtm);

            ctCls.getMethod("execute", desc1).setBody(body1);
            ctCls.getMethod("execute", desc2).setBody(body2);
            ctCls.getMethod("execute", desc3).setBody(body3);

            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(HTTP_SYNC_CLIENT).set(true);
        } catch (Throwable e) {
            logWarn(e, HTTP_SYNC_CLIENT);
        }
    }

    private static void doEnhanceAsyncErrorHandler() {
        if (FLAGS.get(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER).get()) {
            return;
        }
        String body = "{if(this.closed.compareAndSet(false, true)){" +
                HttpClientMoniLogInterceptor.class.getCanonicalName() +
                ".onFailed($1, this.localContext);" +
                "try {this.executionFailed($1);} finally " +
                "{this.discardConnection();this.releaseResources();}}}";
        try {
            CtClass ctCls = getCtClass(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER);
            CtMethod method = ctCls.getMethod("failed", "(Ljava/lang/Exception;)V");
            method.setBody(body);
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER).set(true);
        } catch (Throwable e) {
            logWarn(e, HTTP_ASYNC_CLIENT_EXCHANGE_HANDLER);
        }
    }

    private void enhanceRedisConnFactory() {
        doEnhanceJedisConnFactory();
        doEnhanceLettuceConnFactory();
    }

    private void doEnhanceJedisConnFactory() {
        if (FLAGS.get(JEDIS_CONN_FACTORY).get()) {
            return;
        }
        String methodName1 = "getConnection";
        String methodDesc1 = "()Lorg/springframework/data/redis/connection/RedisConnection;";
        String body1 = "";

        String methodName2 = "getClusterConnection";
        String methodDesc2 = "()Lorg/springframework/data/redis/connection/RedisClusterConnection;";
        String body2 = "";
        try {
            CtClass ctCls = getCtClass(JEDIS_CONN_FACTORY);
            ctCls.getMethod(methodName1, methodDesc1).setBody(body1);
            ctCls.getMethod(methodName2, methodDesc2).setBody(body2);
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(JEDIS_CONN_FACTORY).set(true);
        } catch (Throwable e) {
            logWarn(e, JEDIS_CONN_FACTORY);
        }
    }

    private void doEnhanceLettuceConnFactory() {

    }

    private static void logWarn(Throwable e, String cls) {
        if (e instanceof NotFoundException) {
            return;
        }
        log.warn(INNER_DEBUG_PREFIX + "failed to rebuild [{}]", cls, e);
    }
}