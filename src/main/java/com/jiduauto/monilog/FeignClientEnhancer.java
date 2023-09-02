package com.jiduauto.monilog;

import javassist.ClassPool;
import javassist.CtClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.core.Ordered;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jiduauto.monilog.MoniLogUtil.INNER_DEBUG_PREFIX;

/**
 * 对feignClient增强
 * @author rongjie.yuan
 * @date 2023/9/1 16:43
 */
@Slf4j
public class FeignClientEnhancer implements SpringApplicationRunListener, Ordered {
    private static final String FEIGN_CLIENT = "feign.Client";
    private static final Map<String, AtomicBoolean> FLAGS = new HashMap<String, AtomicBoolean>() {{
        put(FEIGN_CLIENT, new AtomicBoolean());
    }};

    private FeignClientEnhancer(SpringApplication app, String[] args) {
        if (FLAGS.get(FEIGN_CLIENT).get()) {
            return;
        }
        doEnhanceFeignClient();

        SpringApplicationRunListener.super.starting();
    }


    //   "java.lang.reflect.Method m = Client.Default.class.getDeclaredMethod(\"execute\",feign.Request.class, feign.Response.class);" +
    private static boolean doEnhanceFeignClient() {
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
            "try{" +
                "java.lang.reflect.Method m = feign.Client.Default.class.getDeclaredMethod(\"execute\",new Class[]{feign.Request.class, feign.Request.Options.class});" +
                "long cost = endTime-startTime;"+
                FeignMoniLogInterceptor.class.getCanonicalName() + ".doFeignInvocationRecord(m, $1, response, cost, bizException);" +
                "}catch(Throwable e){"+
                    "System.out.println(\"doFeignInvocationRecord2 error\"+e);}"+
            "}" +
            "return response;}";
        try {
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctCls = classPool.getCtClass(FEIGN_CLIENT);
            CtClass[] nestedClasses = ctCls.getNestedClasses();
            nestedClasses[1].getDeclaredMethod("execute").setBody(newMethod);
            nestedClasses[1].toClass();
            nestedClasses[1].writeFile();
            ctCls.writeFile();
            Class<?> targetCls = ctCls.toClass();
            log.info("method of '{}' has bean enhanced.", targetCls.getCanonicalName());
            FLAGS.get(FEIGN_CLIENT).set(true);
        } catch (Throwable e) {
            log.warn(INNER_DEBUG_PREFIX + "failed to rebuild [{}], {}", FEIGN_CLIENT, e.getMessage());
        }
        return false;
    }

    @Override
    public int getOrder() {
        Class[] a = new Class[]{};
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
