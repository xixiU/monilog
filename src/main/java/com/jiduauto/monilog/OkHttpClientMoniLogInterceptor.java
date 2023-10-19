package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Set;

/**
 * OkHttpClient的拦截实现
 * 
 * @author rongjie.yuan
 * @date 2023/10/19 17:35
 */
@Slf4j
public class OkHttpClientMoniLogInterceptor {

    /**
     * 为HttpClient注册拦截器, 注意，此处注册的拦截器仅能处理正常返回的情况，对于异常情况(如超时)则由onFailed方法处理
     * 注：该方法不可修改，包括可见级别，否则将导致HttpClient拦截失效
     */
    @SuppressWarnings("all")
    public static class OkHttpInterceptor implements Interceptor {
        @Override
        public @NotNull Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            //携带有参数的uri
            String host = request.url().host();
            String path = request.url().url().getPath();
            MoniLogProperties.OkHttpClientProperties okHttpClientProperties = checkEnable(host, path);
            if (okHttpClientProperties == null) {
                return chain.proceed(request);
            }
            Throwable bizException = null;
            Response response = null;
            MoniLogParams p = new MoniLogParams();
            try {
                p.setLogPoint(LogPoint.okHttpClient);
                p.setSuccess(true);

                long nowTime = System.currentTimeMillis();
                try{
                    response = chain.proceed(request);
                }catch (Exception e){
                    bizException = e;
                    p.setSuccess(false);
                }
                p.setCost(System.currentTimeMillis() - nowTime);
                Class<?> serviceCls = OkHttpClient.class;
                String methodName = request.method();
                StackTraceElement st = ThreadUtil.getNextClassFromStack(OkHttpClientMoniLogInterceptor.class);
                if (st != null) {
                    try {
                        serviceCls = Class.forName(st.getClassName());
                        methodName = st.getMethodName();
                    } catch (Exception ignore) {
                    }
                }
                p.setServiceCls(serviceCls);
                p.setService(p.getServiceCls().getSimpleName());
                p.setAction(methodName);
                p.setMsgCode(ErrorEnum.SUCCESS.name());
                p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                // TODO rongjie.yuan  2023/10/19 22:36 解析request 与response
                return response;
            }catch (Exception e){
                if (e == bizException) {
                    throw e;
                }
                MoniLogUtil.innerDebug("OkHttpClientMoniLogInterceptor.OkHttpInterceptor.process error", e);
                return response;
            } finally {
                MoniLogUtil.log(p);
            }

        }
    }

    /**
     *
     */
    private static MoniLogProperties.OkHttpClientProperties checkEnable(String host, String path){
        MoniLogProperties mp = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        // 判断开关
        if (mp == null ||
                !mp.isComponentEnable(ComponentEnum.okHttpClient, mp.getOkHttpClient().isEnable())) {
           return null;
        }
        MoniLogProperties.OkHttpClientProperties clientProperties = mp.getOkHttpClient();
        boolean enable = mp.isComponentEnable(ComponentEnum.okHttpClient, clientProperties.isEnable());
        if (!enable) {
            return null;
        }
        Set<String> urlBlackList = clientProperties.getUrlBlackList();
        Set<String> hostBlackList = clientProperties.getHostBlackList();
        enable = !StringUtil.checkPathMatch(urlBlackList, path) && !StringUtil.checkPathMatch(hostBlackList, host);
        return enable ? clientProperties : null;
    }

}
