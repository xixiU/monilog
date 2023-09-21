package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
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
    private static final Set<String> TEXT_TYPES = Sets.newHashSet("application/json", "application/xml", "application/xhtml+xml", "text/");
    private static final Set<String> STREAMING_TYPES = Sets.newHashSet("application/octet-stream", "application/pdf", "application/x-", "image/", "audio/");

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
            String targetHost = host == null ? null : host.getHostName() + (host.getPort() < 0 || host.getPort() == 80 ? "" : ":" + host.getPort());
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
                    if (entity != null) {
                        if (entity.getContentLength() == 0) {
                            bodyParams="";
                        } else if (isStreaming(entity, request.getAllHeaders())) {
                            bodyParams = "Binary Data";
                        } else {
                            BufferedHttpEntity bufferedEntity = new BufferedHttpEntity(entity);
                            bodyParams = EntityUtils.toString(bufferedEntity);
                            ((HttpEntityEnclosingRequest) request).setEntity(bufferedEntity);
                        }
                    }
                }
                List<NameValuePair> params = URLEncodedUtils.parse(uriAndParams.length > 1 ? uriAndParams[1] : null, StandardCharsets.UTF_8);
                Map<String, Collection<String>> queryMap = parseParams(params);
                Map<String, String> headerMap = parseHeaders(request.getAllHeaders());
                JSONObject input = HttpRequestData.of3(bodyParams, queryMap, headerMap).toJSON();
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
                p.setService(p.getServiceCls().getSimpleName());
                p.setAction(methodName);
                p.setInput(new Object[]{input});
                p.setSuccess(true);
                p.setMsgCode(ErrorEnum.SUCCESS.name());
                p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                p.setLogPoint(LogPoint.http_client);

                p.setTags(TagBuilder.of("url", targetHost + path, "method", method).toArray());
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

                HttpEntity entity = httpResponse.getEntity();
                String responseBody;
                JSON jsonBody = null;

                if (entity != null) {
                    if (entity.getContentLength() == 0) {
                        responseBody="";
                    } else if (isStreaming(entity, httpResponse.getAllHeaders())) {
                        responseBody = "Binary Data";
                    } else {
                        BufferedHttpEntity bufferedEntity = new BufferedHttpEntity(entity);
                        responseBody = EntityUtils.toString(bufferedEntity);
                        jsonBody = StringUtil.tryConvert2Json(responseBody);
                        httpResponse.setEntity(bufferedEntity);
                    }
                }else{
                    responseBody="";
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
        MoniLogProperties mp = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        if (mp == null || !mp.isEnable() || mp.getHttpclient() == null) {
            return null;
        }
        MoniLogProperties.HttpClientProperties httpclient = mp.getHttpclient();
        boolean enable = mp.isComponentEnable("httpclient", httpclient.isEnable());
        if (!enable) {
            return null;
        }
        Set<String> urlBlackList = httpclient.getUrlBlackList();
        Set<String> hostBlackList = httpclient.getHostBlackList();
        enable = !checkPathMatch(hostBlackList, host.getHostName()) && !checkPathMatch(urlBlackList, path);
        return enable ? httpclient : null;
    }

    private static boolean isClassEnable(MoniLogProperties.HttpClientProperties httpclient, String invokerClass) {
        Set<String> clientBlackList = httpclient.getClientBlackList();
        return !checkClassMatch(clientBlackList, invokerClass);
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
        if (contentType == null) {
            return entity.isStreaming();
        } else {
            contentType = contentType.toLowerCase();
        }
        for (String textType : TEXT_TYPES) {
            if (contentType.startsWith(textType)) {
                return false;
            }
        }

        for (String streamingType : STREAMING_TYPES) {
            if (contentType.startsWith(streamingType)) {
                return true;
            }
        }
        return false;
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
