package com.jiduauto.log.feignlogspringbootstarter.filter;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FeignClientInterceptor implements RequestInterceptor {
    
    @Override
    public void apply(RequestTemplate requestTemplate) {
        long startTime = System.currentTimeMillis();

        // 获取请求的服务和接口信息
        String serviceName = requestTemplate.feignTarget().name();
        String method = requestTemplate.method();

        // 发送请求并获取返回结果
        Response response = null;//requestTemplate.execute();

        long elapsedTime = System.currentTimeMillis() - startTime;

        // 打印调用信息和耗时
        log.info("FeignClientInterceptor - Service: {} - Method: {} - Elapsed time: {} ms", serviceName, method, elapsedTime);

        // 打印返回值（可选）
        // String responseBody = new String(response.body(), Charset.defaultCharset());
        // log.info("FeignClientInterceptor - Response: {}", responseBody);
    }
}