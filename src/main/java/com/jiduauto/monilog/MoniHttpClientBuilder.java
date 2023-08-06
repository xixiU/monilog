package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.Joinpoint;
import org.apache.http.*;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * @author yp
 * @date 2023/07/31
 */
@Slf4j
public class MoniHttpClientBuilder extends HttpClientBuilder {
    //允许业务方使用此方法直接创建HttpClientBuilder
    public static HttpClientBuilder create() {
        return addInterceptors(new MoniHttpClientBuilder());
    }

    static HttpClientBuilder getProxyBean(HttpClientBuilder bean) {
        HttpClientBuilder builder = ProxyUtils.getProxy(bean, Joinpoint::proceed);
        return addInterceptors(builder);
    }

    private static HttpClientBuilder addInterceptors(HttpClientBuilder builder) {
        return builder.addInterceptorFirst(new RequestInterceptor()).addInterceptorLast(new ResponseInterceptor());
    }

    private static class RequestInterceptor implements HttpRequestInterceptor {
        @Override
        public void process(HttpRequest httpRequest, HttpContext httpContext) throws HttpException, IOException {
            RequestLine requestLine = httpRequest.getRequestLine();
            MoniLogParams p = new MoniLogParams();
            p.setCost(System.currentTimeMillis());
            //TODO
//            p.setService();
//            p.setAction();
//            p.setServiceCls();
//            p.setInput();
            p.setSuccess(true);
            p.setMsgCode(ErrorEnum.SUCCESS.name());
            p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            p.setLogPoint(LogPoint.http_client);
            p.setTags(TagBuilder.of("url", requestLine.getUri(), "method", requestLine.getMethod()).toArray());
            httpContext.setAttribute("__MoniLogParams", p);
        }
    }

    private static class ResponseInterceptor implements HttpResponseInterceptor {
        @Override
        public void process(HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
            MoniLogParams p = (MoniLogParams) httpContext.getAttribute("__MoniLogParams");
            if (p == null) {
                return;
            }
            if (p.getCost() > 0) {
                p.setCost(System.currentTimeMillis() - p.getCost());
            }
            StatusLine statusLine = httpResponse.getStatusLine();
            p.setSuccess(statusLine.getStatusCode() == HttpStatus.SC_OK);
            p.setMsgCode(String.valueOf(statusLine.getStatusCode()));
            log.info("httpclient monilog execute... to be implemented");
            //TODO
//            p.setOutput();
//            p.setMsgInfo();
//            p.setException();
            httpContext.removeAttribute("__MoniLogParams");
            MoniLogUtil.log(p);
        }
    }
}
