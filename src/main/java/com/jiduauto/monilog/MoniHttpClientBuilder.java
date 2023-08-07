package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

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
            String targetHost = (String) httpContext.getAttribute(HttpClientContext.HTTP_TARGET_HOST);
            String method = httpRequest.getRequestLine().getMethod();
            RequestLine requestLine = httpRequest.getRequestLine();
            //header
            Header[] allHeaders = httpRequest.getAllHeaders();
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
            //TODO
//            p.setOutput();
//            p.setMsgInfo();
//            p.setException();
            httpContext.removeAttribute(MONILOG_PARAMS_KEY);
            MoniLogUtil.log(p);
        }
    }
}
