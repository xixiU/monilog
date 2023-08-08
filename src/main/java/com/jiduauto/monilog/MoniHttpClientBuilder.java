package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author yp
 * @date 2023/07/31
 */
@Slf4j
public class MoniHttpClientBuilder extends HttpClientBuilder {
    private static final String MONILOG_PARAMS_KEY = "__MoniLogParams";
    private static final String MONILOG_REQ_INTERCEPATOR = "__MoniLogRequestInterceptor";
    private static final String MONILOG_RESP_INTERCEPATOR = "__MoniLogResponseInterceptor";

    //允许业务方使用此方法直接创建HttpClientBuilder
    public static HttpClientBuilder create() {
        return new MoniHttpClientBuilder();
    }

    @Override
    public final CloseableHttpClient build() {
        addInterceptors(this);
        return super.build();
    }

    /**
     * 该方法不可修改，包括可见级别
     *
     * @param builder
     * @return
     */
    public static HttpClientBuilder addInterceptors(HttpClientBuilder builder) {
        return builder.addInterceptorFirst(new RequestInterceptor()).addInterceptorLast(new ResponseInterceptor());
    }

    private static class RequestInterceptor implements HttpRequestInterceptor {
        @Override
        public void process(HttpRequest request, HttpContext httpContext) throws HttpException, IOException {
            if (httpContext.getAttribute(MONILOG_REQ_INTERCEPATOR) != null) {
                return;
            }
            try {
                String method = request.getRequestLine().getMethod();
                String bodyParams = null;
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    String contentType = entity == null ? null : (entity.getContentType() == null ? null : entity.getContentType().getValue());
                    if (entity != null) {
                        if (isUpstream(method, contentType)) {
                            bodyParams = "Binary Data";
                        } else {
                            BufferedHttpEntity bufferedEntity = new BufferedHttpEntity(entity);
                            bodyParams = EntityUtils.toString(bufferedEntity);
                            ((HttpEntityEnclosingRequest) request).setEntity(bufferedEntity);
                        }
                    }
                }
                //携带有参数的uri
                String[] uriAndParams = request.getRequestLine().getUri().split("\\?");
                String path = uriAndParams[0];
                List<NameValuePair> params = URLEncodedUtils.parse(uriAndParams.length > 1 ? uriAndParams[1] : null, StandardCharsets.UTF_8);
                Map<String, Collection<String>> queryMap = parseParams(params);
                Map<String, String> headerMap = parseHeaders(request.getAllHeaders());
                JSONObject input = HttpRequestData.of3(bodyParams, queryMap, headerMap).toJSON();
                StackTraceElement st = ThreadUtil.getNextClassFromStack(MoniHttpClientBuilder.class, "org.apache");
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

                HttpHost host = (HttpHost) httpContext.getAttribute(HttpClientContext.HTTP_TARGET_HOST);
                String targetHost = host == null ? null : host.getHostName() + (host.getPort() < 0 || host.getPort() == 80 ? "" : ":" + host.getPort());
                p.setTags(TagBuilder.of("url", targetHost + path, "method", method).toArray());
                httpContext.setAttribute(MONILOG_PARAMS_KEY, p);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("HttpClient.RequestInterceptor.process error", e);
            } finally {
                httpContext.setAttribute(MONILOG_REQ_INTERCEPATOR, 1);
            }
        }
    }

    private static class ResponseInterceptor implements HttpResponseInterceptor {
        @Override
        public void process(HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
            if (httpContext.getAttribute(MONILOG_RESP_INTERCEPATOR) != null) {
                return;
            }
            MoniLogParams p = (MoniLogParams) httpContext.getAttribute(MONILOG_PARAMS_KEY);
            if (p == null) {
                return;
            }
            httpContext.setAttribute(MONILOG_RESP_INTERCEPATOR, 1);
            try {
                if (p.getCost() > 0) {
                    p.setCost(System.currentTimeMillis() - p.getCost());
                }
                StatusLine statusLine = httpResponse.getStatusLine();
                p.setSuccess(statusLine.getStatusCode() < HttpStatus.SC_BAD_REQUEST);
                p.setMsgCode(String.valueOf(statusLine.getStatusCode()));
                String contentType = null;
                String contentDisposition = null;
                for (Header h : httpResponse.getAllHeaders()) {
                    String name = h.getName();
                    String value = h.getValue();
                    if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                        contentType = value;
                    }
                    if (HttpHeaders.CONTENT_DISPOSITION.equalsIgnoreCase(name)) {
                        contentDisposition = value;
                    }
                }
                String responseBody;
                JSON jsonBody = null;
                if (isDownstream(contentDisposition)) {
                    responseBody = "Binary Data";
                } else {
                    if (isJson(contentType)) {
                        HttpEntity entity = httpResponse.getEntity();
                        BufferedHttpEntity bufferedEntity = new BufferedHttpEntity(entity);
                        responseBody = EntityUtils.toString(bufferedEntity);
                        jsonBody = StringUtil.tryConvert2Json(responseBody);
                        httpResponse.setEntity(bufferedEntity);
                    } else {
                        responseBody = "[content of\"" + contentType + "\"...]";
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

    private static boolean isUpstream(String method, String contentType) {
        return HttpPost.METHOD_NAME.equalsIgnoreCase(method) && contentType.toLowerCase(Locale.ENGLISH).startsWith("multipart/");
    }

    private static boolean isDownstream(String contentDisposition) {
        return StringUtils.isNotBlank(contentDisposition) &&
                StringUtils.containsIgnoreCase(contentDisposition, "attachment") || StringUtils.containsIgnoreCase(contentDisposition, "filename");
    }

    private static boolean isJson(String contentType) {
        if (isDownstream(contentType)) {
            return false;
        }
        return StringUtils.containsIgnoreCase(contentType, "application/json");
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
