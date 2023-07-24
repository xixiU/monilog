package com.jiduauto.log.grpclogspringbootstarter.filter;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * @author fan.zhang02
 * @date 2023/07/21/15:29
 */
@Slf4j
public class GrpcLogPrintClientInterceptor implements ClientInterceptor {
    InheritableThreadLocal<Map<String, Object>> threadLocal = new InheritableThreadLocal<Map<String, Object>>();

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {
        // 实现 interceptCall 方法来拦截 gRPC 客户端的请求和响应
        String fullMethodName = method.getFullMethodName();
        String serviceName = next.authority();

        return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // 在请求发送前进行处理
                // 这里你可以获取请求的入参和其他相关信息
                log.info("ffff");
//                System.out.println("Intercepting method: " + method.getFullMethodName());
//                System.out.println("Request: " + request);

                super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onMessage(RespT message) {
                        // 在响应返回后进行处理
                        // 这里你可以获取响应的结果和其他相关信息
                        System.out.println("Response: " + message);
                        super.onMessage(message);
                    }
                }, headers);

            }

            @Override
            public void sendMessage(ReqT message) {
                super.sendMessage(message);
            }


        };
    }
}
