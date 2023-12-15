package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.jiduauto.monilog.StringUtil.checkClassMatch;

/**
 * OkHttpClient的拦截实现,
 * OKHttpClient的同步和异步拦截最后都是通过okhttp3.RealCall#getResponseWithInterceptorChain()执行，参考：https://blog.csdn.net/weixin_41939525/article/details/106419678
 *
 * @author rongjie.yuan
 * @date 2023/10/19 17:35
 */
@Slf4j
@SuppressWarnings("all")
public final class OkHttpClientMoniLogInterceptor implements Interceptor {
        @Override
        public @NotNull Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            //携带有参数的uri
            String host = request.url().host();
            String path = request.url().url().getPath();
            MoniLogProperties.HttpClientProperties httpClientProperties = checkEnable(host, path);
            if (httpClientProperties == null) {
                return chain.proceed(request);
            }
            StackTraceElement st = ThreadUtil.getNextClassFromStack(OkHttpClientMoniLogInterceptor.class);
            if (!isClassEnable(httpClientProperties, st == null ? null : st.getClassName())) {
                return chain.proceed(request);
            }
            Throwable bizException = null;
            Response response = null;
            MoniLogParams p = new MoniLogParams();
            try {
                p.setLogPoint(LogPoint.http_client);
                long nowTime = System.currentTimeMillis();
                try {
                    response = chain.proceed(request);
                    p.setSuccess(true);
                    p.setMsgCode(ErrorEnum.SUCCESS.name());
                    p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                } catch (Exception e) {
                    bizException = e;
                    p.setSuccess(false);
                    p.setException(e);
                    ErrorInfo errorInfo = ExceptionUtil.parseException(e);
                    if (errorInfo != null) {
                        p.setMsgCode(errorInfo.getErrorCode());
                        p.setMsgInfo(errorInfo.getErrorMsg());
                    }
                }
                p.setCost(System.currentTimeMillis() - nowTime);
                Class<?> serviceCls = OkHttpClient.class;
                String methodName = request.method();
                if (st != null) {
                    try {
                        serviceCls = Class.forName(st.getClassName());
                        methodName = st.getMethodName();
                    } catch (Exception ignore) {}
                }
                p.setServiceCls(serviceCls);
                p.setService(ReflectUtil.getSimpleClassName(p.getServiceCls()));
                p.setAction(methodName);
                p.setTags(TagBuilder.of("url", HttpRequestData.extractPath(request.url().toString()), "method", request.method()).toArray());
                p.setInput(new Object[]{getInputObject(request)});
                if (response != null) {
                    // 先塞调用的结果
                    p.setMsgCode(String.valueOf(response.code()));
                    String responseBody = getOutputBody(response.body());
                    JSON jsonBody = StringUtil.tryConvert2Json(responseBody);
                    p.setOutput(jsonBody == null ? responseBody : jsonBody);
                    ParsedResult pr = ResultParseUtil.parseResult(jsonBody, null, null, httpClientProperties.getDefaultBoolExpr(), null, null);
                    if (pr != null) {
                        p.setSuccess(p.isSuccess() && pr.isSuccess());
                        if (StringUtils.isNotBlank(pr.getMsgCode())) {
                            p.setMsgCode(pr.getMsgCode());
                        }
                        if (StringUtils.isNotBlank(pr.getMsgInfo())) {
                            p.setMsgInfo(pr.getMsgInfo());
                        }
                    }
                }
                return response;
            } catch (Exception e) {
                if (e == bizException) {
                    throw e;
                }
                MoniLogUtil.innerDebug("OkHttpInterceptor.intercept error", e);
                return response;
            } finally {
                MoniLogUtil.log(p);
            }
    }

    /**
     * 校验是否开启
     */
    private static MoniLogProperties.HttpClientProperties checkEnable(String host, String path) {
        // 判断开关
        if (!ComponentEnum.httpclient.isEnable()) {
            return null;
        }
        MoniLogProperties mp = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        assert mp != null;
        MoniLogProperties.HttpClientProperties clientProperties = mp.getHttpclient();
        Set<String> urlBlackList = clientProperties.getUrlBlackList();
        Set<String> hostBlackList = clientProperties.getHostBlackList();
        return StringUtil.checkPathMatch(urlBlackList, path) || StringUtil.checkPathMatch(hostBlackList, host) ? null : clientProperties;
    }

    private static boolean isClassEnable(MoniLogProperties.HttpClientProperties httpclient, String invokerClass) {
        Set<String> clientBlackList = httpclient.getClientBlackList();
        return !checkClassMatch(clientBlackList, invokerClass);
    }

    private static JSONObject getInputObject(Request request) {
        // 请求路径
        String url = request.url().toString();

        String bodyParams = null;
        // 请求体参数
        RequestBody requestBody = request.body();
        if (requestBody != null) {
            bodyParams = getInputBodyParams(request);
        }

        // 请求头参数
        Map<String, String> headerMap = new HashMap<>();
        Headers headers = request.headers();
        for (int i = 0, size = headers.size(); i < size; i++) {
            String name = headers.name(i);
            String value = headers.value(i);
            headerMap.put(name, value);
        }

        Map<String, Collection<String>> queryMap = new HashMap<>();
        // 查询参数
        HttpUrl httpUrl = request.url();
        for (int i = 0, size = httpUrl.querySize(); i < size; i++) {
            String name = httpUrl.queryParameterName(i);
            String value = httpUrl.queryParameterValue(i);
            Collection<String> valueList = queryMap.computeIfAbsent(name, item -> new ArrayList<>());
            valueList.add(value);
        }
        return HttpRequestData.of3(url, bodyParams, queryMap, headerMap).toJSON();
    }

    private static boolean isInputStream(RequestBody requestBody){
        if (requestBody instanceof MultipartBody) {
            return true;
        }
        MediaType mediaType = requestBody.contentType();
        if (mediaType == null) {
            // 未知状态返回false
            return true;
        }
        Boolean isStream = HttpUtil.checkContentTypeIsStream(mediaType.toString());
        return Boolean.TRUE.equals(isStream);
    }

    private static String getInputBodyParams(Request request){
        try {
            Request copy = request.newBuilder().build();
            Buffer buffer = new Buffer();
            RequestBody body = copy.body();
            if (body == null) {
                return null;
            }
            if (isInputStream(body)) {
                return "Binary Data";
            }
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            MoniLogUtil.innerDebug("OkHttpClientMoniLogInterceptor.getInputBodyParams error", e);
            return null;
        }
    }
    private static String getOutputBody(ResponseBody responseBody) {
        if (responseBody == null) {
            return null;
        }
        String responseBodyString = null;
        try {
            BufferedSource source = responseBody.source();
            source.request(Long.MAX_VALUE); // request the entire body.
            Buffer buffer = source.getBuffer();
            responseBodyString = buffer.clone().readString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            MoniLogUtil.innerDebug("OkHttpClientMoniLogInterceptor.getOutputBody error", e);
        }
        return responseBodyString;
    }

}
