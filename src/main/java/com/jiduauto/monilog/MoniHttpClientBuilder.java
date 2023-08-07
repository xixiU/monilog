package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yp
 * @date 2023/07/31
 */
@Slf4j
public class MoniHttpClientBuilder extends HttpClientBuilder {
    private static final String MONILOG_PARAMS_KEY = "__MoniLogParams";

    //允许业务方使用此方法直接创建HttpClientBuilder
    public static HttpClientBuilder create() {
        return addInterceptors(new MoniHttpClientBuilder());
    }

    static HttpClientBuilder addInterceptors(HttpClientBuilder builder) {
        return builder.addInterceptorFirst(new RequestInterceptor()).addInterceptorLast(new ResponseInterceptor());
    }

    private static class RequestInterceptor implements HttpRequestInterceptor {
        @Override
        public void process(HttpRequest httpRequest, HttpContext httpContext) throws HttpException, IOException {
            HttpUriRequest request;
            if (httpRequest instanceof HttpRequestBase) {
                request = ((HttpRequestBase) httpRequest);
            } else if (httpRequest instanceof HttpRequestWrapper) {
                request = (HttpRequestWrapper) httpRequest;
            } else {
                MoniLogUtil.innerDebug("unsupported httpRequestType:{}", httpRequest.getClass());
                return;
            }
            String uri = request.getURI().toString(); //携带有参数的uri
            String targetHost = String.valueOf(httpContext.getAttribute(HttpClientContext.HTTP_TARGET_HOST));
            String method = request.getRequestLine().getMethod();
            //headers
            Map<String, String> headerMap = parseHeaders(request.getAllHeaders());
            //body
            //params
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
            boolean isUpload = false;
            MoniLogParams p = new MoniLogParams();
            p.setCost(System.currentTimeMillis());
            p.setServiceCls(serviceCls);
            p.setService(p.getServiceCls().getSimpleName());
            p.setAction(methodName);
//            p.setInput(formatInput);
            p.setSuccess(true);
            p.setMsgCode(ErrorEnum.SUCCESS.name());
            p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            p.setLogPoint(LogPoint.http_client);
            p.setTags(TagBuilder.of("url", targetHost, "method", method).toArray());
            httpContext.setAttribute(MONILOG_PARAMS_KEY, p);
        }
    }

    private static class ResponseInterceptor implements HttpResponseInterceptor {
        @Override
        public void process(HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
            MoniLogParams p = (MoniLogParams) httpContext.getAttribute(MONILOG_PARAMS_KEY);
            if (p == null) {
                return;
            }
            if (p.getCost() > 0) {
                p.setCost(System.currentTimeMillis() - p.getCost());
            }
            StatusLine statusLine = httpResponse.getStatusLine();
            p.setSuccess(statusLine.getStatusCode() < HttpStatus.SC_BAD_REQUEST);
            p.setMsgCode(String.valueOf(statusLine.getStatusCode()));

            Header[] ct = httpResponse.getHeaders(HttpHeaders.CONTENT_TYPE);
            //要判断响应类型，处理上传下载等特殊情况
            for (Header h : httpResponse.getAllHeaders()) {
                String name = h.getName();
                String value = h.getValue();
            }

            HttpEntity entity = httpResponse.getEntity();
            BufferedHttpEntity bufferedEntity = new BufferedHttpEntity(entity);
            String content = EntityUtils.toString(bufferedEntity);
            httpResponse.setEntity(bufferedEntity);
            boolean isDownload = false;

            //TODO
            p.setOutput(content);
//            p.setMsgInfo();
//            p.setException();
            httpContext.removeAttribute(MONILOG_PARAMS_KEY);
            MoniLogUtil.log(p);
        }
    }


    private boolean isDownstream() {
        String header = getFirstHeader(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION);
        return StringUtils.containsIgnoreCase(header, "attachment") || StringUtils.containsIgnoreCase(header, "filename");
    }

    private boolean isJson() {
        if (isDownstream()) {
            return false;
        }
        String header = getFirstHeader(org.springframework.http.HttpHeaders.CONTENT_TYPE);
        return StringUtils.containsIgnoreCase(header, "application/json");
    }

    private String getFirstHeader(String name) {
        if (headers() == null || StringUtils.isBlank(name)) {
            return null;
        }
        for (Map.Entry<String, Collection<String>> me : headers().entrySet()) {
            if (me.getKey().equalsIgnoreCase(name)) {
                Collection<String> headers = me.getValue();
                if (headers == null || headers.isEmpty()) {
                    return null;
                }
                return headers.iterator().next();
            }
        }
        return null;
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
}
