package com.jiduauto.monitor.log;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * @author yp
 * @date 2023/07/31
 */
@Slf4j
public class XHttpClientBuilder extends HttpClientBuilder {
    public XHttpClientBuilder() {
        super();
    }

    public static XHttpClientBuilder create() {
        return new XHttpClientBuilder();
    }

    @Override
    public CloseableHttpClient build() {
        this.addInterceptorFirst(new RequestInterceptor());
        this.addInterceptorLast(new ResponseInterceptor());
        return super.build();
    }

    static class RequestInterceptor implements HttpRequestInterceptor {
        @Override
        public void process(HttpRequest httpRequest, HttpContext httpContext) throws HttpException, IOException {
            RequestLine requestLine = httpRequest.getRequestLine();
            MonitorLogParams p = new MonitorLogParams();
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
            httpContext.setAttribute("__MonitorLogParams", p);
        }
    }

    static class ResponseInterceptor implements HttpResponseInterceptor {
        @Override
        public void process(HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
            MonitorLogParams p = (MonitorLogParams) httpContext.getAttribute("__MonitorLogParams");
            if (p == null) {
                return;
            }
            if (p.getCost() > 0) {
                p.setCost(System.currentTimeMillis() - p.getCost());
            }
            StatusLine statusLine = httpResponse.getStatusLine();
            p.setSuccess(statusLine.getStatusCode() == HttpStatus.SC_OK);
            p.setMsgCode(String.valueOf(statusLine.getStatusCode()));
            log.info("httpclient monitor execute... to be implemented");
            //TODO
//            p.setOutput();
//            p.setMsgInfo();
//            p.setException();
            httpContext.removeAttribute("__MonitorLogParams");
            MonitorLogUtil.log(p);
        }
    }
}
