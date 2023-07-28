package com.jiduauto.monitor.log;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

import java.io.IOException;

@Slf4j
public class HttpClientLogMonitorInterceptor {
    @AllArgsConstructor
    static class HttpClientBuilderProcessor implements BeanPostProcessor, Ordered {
        private final MonitorLogProperties.HttpClientProperties httpClientProperties;
        private final RequestInterceptor requestInterceptor;
        private final ResponseInterceptor responseInterceptor;

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof HttpClientBuilder && !(bean instanceof EnhancedHttpClientBuilder)) {
                return new EnhancedHttpClientBuilder((HttpClientBuilder) bean, httpClientProperties, requestInterceptor, responseInterceptor);
            }
            return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
        }

        @Override
        public int getOrder() {
            return Integer.MAX_VALUE;
        }

    }

    @AllArgsConstructor
    private static class EnhancedHttpClientBuilder extends HttpClientBuilder {
        private final HttpClientBuilder delegate;
        private final MonitorLogProperties.HttpClientProperties httpClientProperties;
        private final RequestInterceptor requestInterceptor;
        private final ResponseInterceptor responseInterceptor;

        @Override
        public CloseableHttpClient build() {
            delegate.addInterceptorFirst(requestInterceptor);
            delegate.addInterceptorLast(responseInterceptor);
            return delegate.build();
        }
    }

    public static class RequestInterceptor implements HttpRequestInterceptor {
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

    public static class ResponseInterceptor implements HttpResponseInterceptor {
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
            //TODO
//            p.setOutput();
//            p.setMsgInfo();
//            p.setException();
            httpContext.removeAttribute("__MonitorLogParams");
            MonitorLogUtil.log(p);
        }
    }
}