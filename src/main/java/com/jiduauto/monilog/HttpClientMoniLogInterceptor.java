package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.DecompressingEntity;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.jiduauto.monilog.StringUtil.checkClassMatch;
import static com.jiduauto.monilog.StringUtil.checkPathMatch;

/**
 * 该类签名不可修改，包括可见级别，否则将导致HttpClient拦截失效
 *
 * @author yp
 * @date 2023/07/31
 */
@Slf4j
public final class HttpClientMoniLogInterceptor {
    private static final String MONILOG_PARAMS_KEY = "__MoniLogParams";
    private static final String ResponseEntityProxy = "org.apache.http.impl.execchain.ResponseEntityProxy";

    /**
     * 为HttpClient注册拦截器, 注意，此处注册的拦截器仅能处理正常返回的情况，对于异常情况(如超时)则由onFailed方法处理
     * 注：该方法不可修改，包括可见级别，否则将导致HttpClient拦截失效
     */
    public static void addInterceptorsForBuilder(HttpClientBuilder builder) {
        builder.addInterceptorFirst(new RequestInterceptor()).addInterceptorLast(new ResponseInterceptor());
    }

    /**
     * 为AsyncHttpClient注册拦截器，注意，此处注册的拦截器仅能处理正常返回的情况，对于异常情况(如超时)则由onFailed方法处理
     * 注：该方法不可修改，包括可见级别，否则将导致HttpClient拦截失效
     */
    public static void addInterceptorsForAsyncBuilder(HttpAsyncClientBuilder builder) {
        builder.addInterceptorFirst(new RequestInterceptor()).addInterceptorLast(new ResponseInterceptor());
    }

    private static class RequestInterceptor implements HttpRequestInterceptor {
        @Override
        public void process(HttpRequest request, HttpContext httpContext) throws HttpException, IOException {
            RequestLine requestLine = request.getRequestLine();
            HttpHost host = (HttpHost) httpContext.getAttribute(HttpClientContext.HTTP_TARGET_HOST);
//            String targetHost = host == null ? null : host.getHostName() + (host.getPort() < 0 || host.getPort() == 80 ? "" : ":" + host.getPort());
            //携带有参数的uri
            String[] uriAndParams = requestLine.getUri().split("\\?");
            String path = uriAndParams[0];
            MoniLogProperties.HttpClientProperties httpClientProperties = checkEnable(host, path);
            if (httpClientProperties == null) {//fail-fast
                return;
            }
            StackTraceElement st = ThreadUtil.getNextClassFromStack(HttpClientMoniLogInterceptor.class);
            if (!isClassEnable(httpClientProperties, st == null ? null : st.getClassName())) {
                return;
            }
            try {
                String method = requestLine.getMethod();
                String bodyParams = null;
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    if (isValidEntity(entity)) {
                        if (isStreaming(entity, request.getAllHeaders())) {
                            bodyParams = "Binary Data";
                        } else {
                            BufferedHttpEntity bufferedEntity = getEntity(entity);
                            bodyParams = EntityUtils.toString(bufferedEntity);
                            ((HttpEntityEnclosingRequest) request).setEntity(bufferedEntity);
                        }
                    }
                }
                List<NameValuePair> params = URLEncodedUtils.parse(uriAndParams.length > 1 ? uriAndParams[1] : null, StandardCharsets.UTF_8);
                Map<String, Collection<String>> queryMap = parseParams(params);
                Map<String, String> headerMap = parseHeaders(request.getAllHeaders());
                JSONObject input = HttpRequestData.of3(requestLine.getUri(), bodyParams, queryMap, headerMap).toJSON();
                Class<?> serviceCls = HttpClient.class;
                String methodName = method;
                if (st != null) {
                    try {
                        serviceCls = Class.forName(st.getClassName());
                        methodName = st.getMethodName();
                    } catch (Exception ignore) {
                    }
                }
                MoniLogParams p = new MoniLogParams();
                p.setCost(System.currentTimeMillis());
                p.setServiceCls(serviceCls);
                p.setService(ReflectUtil.getSimpleClassName(p.getServiceCls()));
                p.setAction(methodName);
                p.setInput(new Object[]{input});
                p.setSuccess(true);
                p.setMsgCode(ErrorEnum.SUCCESS.name());
                p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                p.setLogPoint(LogPoint.http_client);

                p.setTags(TagBuilder.of("url", HttpUtil.extractPathWithoutPathParams(path), "method", method).toArray());
                httpContext.setAttribute(MONILOG_PARAMS_KEY, p);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("HttpClient.RequestInterceptor.process error", e);
            }
        }
    }


    private static class ResponseInterceptor implements HttpResponseInterceptor {
        @Override
        public void process(HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
            MoniLogParams p = (MoniLogParams) httpContext.getAttribute(MONILOG_PARAMS_KEY);
            if (p == null) {
                return;
            }
            try {
                if (p.getCost() > 0) {
                    p.setCost(System.currentTimeMillis() - p.getCost());
                }
                StatusLine statusLine = httpResponse.getStatusLine();
                p.setSuccess(statusLine.getStatusCode() < HttpStatus.SC_BAD_REQUEST);
                p.setMsgCode(String.valueOf(statusLine.getStatusCode()));
                if (!p.isSuccess()) {
                    p.setMsgInfo(ErrorEnum.FAILED.getMsg());
                }

                HttpEntity entity = httpResponse.getEntity();
                String responseBody = null;
                JSON jsonBody = null;

                if (isValidEntity(entity)) {
                    if (isStreaming(entity, httpResponse.getAllHeaders())) {
                        responseBody = "Binary Data";
                    } else {
                        if (ResponseEntityProxy.equals(entity.getClass().getCanonicalName())) {
                            String entityField = "wrappedEntity";
                            HttpEntity innerEntity = ReflectUtil.getPropValue(entity, entityField);
                            if (isValidEntity(innerEntity)) {
                                if (isStreaming(innerEntity, httpResponse.getAllHeaders())) {
                                    responseBody = "Binary Data";
                                } else {
                                    BufferedHttpEntity bufferedEntity = new MonilogBufferedHttpEntity(innerEntity);
                                    responseBody = EntityUtils.toString(bufferedEntity);
                                    jsonBody = StringUtil.tryConvert2Json(responseBody);
                                    ReflectUtil.setPropValue(entity, entityField, bufferedEntity, false);
                                }
                            } else {
                                responseBody = "[parseResponseDataFailed]";
                            }
                        } else {
                            HttpEntity bufferedEntity = null;
                            if (entity instanceof DecompressingEntity) {
                                bufferedEntity = new DecompressingEntityWrapper(entity);
                            } else {
                                bufferedEntity = getEntity(entity);
                            }
                            responseBody = EntityUtils.toString(bufferedEntity);
                            jsonBody = StringUtil.tryConvert2Json(responseBody);
                            httpResponse.setEntity(bufferedEntity);
                        }
                    }
                }

                p.setOutput(jsonBody == null ? responseBody : jsonBody);
                if (jsonBody != null) {
                    MoniLogProperties prop = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
                    String defaultBoolExpr = null;
                    if (prop != null) {
                        defaultBoolExpr = prop.getHttpclient().getDefaultBoolExpr();
                    }
                    ParsedResult pr = ResultParseUtil.parseResult(jsonBody, null, null, defaultBoolExpr, null, null);
                    if (p.isSuccess()) {
                        //如果外层响应码是200，则再看内层是否成功
                        p.setSuccess(pr.isSuccess());
                    }
                    if (StringUtils.isNotBlank(pr.getMsgCode())) {
                        p.setMsgCode(pr.getMsgCode());
                    }
                    if (StringUtils.isNotBlank(pr.getMsgInfo())) {
                        p.setMsgInfo(pr.getMsgInfo());
                    }
                }
            } catch (Exception e) {
                MoniLogUtil.innerDebug("HttpClient.ResponseInterceptor.process error", e);
            } finally {
                httpContext.removeAttribute(MONILOG_PARAMS_KEY);
                MoniLogUtil.log(p);
            }
        }
    }

    private static BufferedHttpEntity getEntity(HttpEntity entity) throws IOException, InvocationTargetException, IllegalAccessException {
        BufferedHttpEntity bufferedEntity = new BufferedHttpEntity(entity);
        // bos 中的都是RestartableInputStream的子类
        // 此处不引入bce,直接通过包名和方法判断是否为RestartableInputStream子类
        Class<? extends InputStream> aClass = entity.getContent().getClass();
        if (!aClass.getCanonicalName().contains("com.baidubce.internal")) {
            return bufferedEntity;
        }
        Method restart = cn.hutool.core.util.ReflectUtil.getMethodByName(aClass, "restart");
        if (restart != null) {
            restart.invoke(entity.getContent());
        }

        return bufferedEntity;
    }

    private static class MonilogBufferedHttpEntity extends BufferedHttpEntity {
        public MonilogBufferedHttpEntity(HttpEntity entity) throws IOException {
            super(entity);
        }

        @Override
        public boolean isChunked() {
            return this.wrappedEntity.isChunked();
        }

        @Override
        public boolean isStreaming() {
            return wrappedEntity.isStreaming();
        }
    }

    private static class DecompressingEntityWrapper extends HttpEntityWrapper {

        @Getter
        private final byte[] buffer;

        public DecompressingEntityWrapper(HttpEntity wrapped) throws IOException{
            super(wrapped);
            try {
                this.buffer = EntityUtils.toByteArray(wrapped);
            } catch (IOException e) {
                throw new RuntimeException("Failed to obtain content stream from wrapped entity", e);
            }
        }

        @Override
        public InputStream getContent() throws IOException {
            return new ByteArrayInputStream(buffer);
        }
    }


    /**
     * HttpClient在执行异常时的回调方法
     * 注意：该类不可修改，包括可见级别，否则将导致HttpClient在异常时拦截失效
     * 该方法不可抛异常
     */
    public static void onFailed(Throwable ex, HttpContext ctx) {
        MoniLogParams p = ctx == null ? null : (MoniLogParams) ctx.getAttribute(MONILOG_PARAMS_KEY);
        if (p == null) {
            return;
        }
        try {
            if (p.getCost() > 0) {
                p.setCost(System.currentTimeMillis() - p.getCost());
            }
            p.setSuccess(false);
            ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
            if (errorInfo == null) {
                errorInfo = ErrorInfo.of(ErrorEnum.SYSTEM_ERROR.name(), "HttpClientExecuteFailed");
            }
            p.setMsgCode(errorInfo.getErrorCode());
            p.setMsgInfo(errorInfo.getErrorMsg());
            p.setException(ex);
        } catch (Throwable e) {
            MoniLogUtil.innerDebug("HttpClient.execute error", e);
        } finally {
            ctx.removeAttribute(MONILOG_PARAMS_KEY);
            MoniLogUtil.log(p);
        }
    }

    //启用，则返回当前配置对象供后续链路使用，否则返回null
    private static MoniLogProperties.HttpClientProperties checkEnable(HttpHost host, String path) {
        if (!ComponentEnum.httpclient.isEnable()) {
            return null;
        }
        MoniLogProperties mp = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        assert mp != null;
        MoniLogProperties.HttpClientProperties httpclient = mp.getHttpclient();
        Set<String> urlBlackList = httpclient.getUrlBlackList();
        Set<String> hostBlackList = httpclient.getHostBlackList();
        boolean enable = !checkPathMatch(hostBlackList, host.getHostName()) && !checkPathMatch(urlBlackList, path);
        return enable ? httpclient : null;
    }

    private static boolean isClassEnable(MoniLogProperties.HttpClientProperties httpclient, String invokerClass) {
        Set<String> clientBlackList = httpclient.getClientBlackList();
        return !checkClassMatch(clientBlackList, invokerClass);
    }

    private static boolean isValidEntity(HttpEntity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return entity.getContent() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isStreaming(HttpEntity entity, Header[] headers) {
        if (entity instanceof FileEntity) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(entity.getClass().getCanonicalName(), "Multipart")) {
            return true;
        }
        if (entity instanceof StringEntity) {
            return false;
        }
        Header ct = entity.getContentType();
        String contentType = ct == null ? null : ct.getValue();
        if (contentType == null) {
            for (Header header : headers) {
                if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(header.getName())) {
                    contentType = header.getValue();
                    break;
                }
            }
        }
        Boolean isStream = HttpUtil.checkContentTypeIsStream(contentType);
        return isStream == null ? entity.isStreaming() : isStream;
    }

    private static Map<String, String> parseHeaders(Header[] allHeaders) {
        Map<String, String> map = new HashMap<>();
        if (allHeaders != null) {
            for (Header h : allHeaders) {
                String name = h.getName();
                String value = h.getValue();
                map.put(name, value);
            }
        }
        return map;
    }

    private static Map<String, Collection<String>> parseParams(List<NameValuePair> params) {
        Map<String, Collection<String>> map = new HashMap<>();
        if (CollectionUtils.isNotEmpty(params)) {
            for (NameValuePair param : params) {
                Collection<String> list = map.get(param.getName());
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(param.getValue());
                map.put(param.getName(), list);
            }
        }
        return map;
    }
}
