package com.jiduauto.log.grpc.filter;

import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.grpc.GrpcMonitorLogServerCall;
import io.grpc.*;
import io.grpc.ServerCall.Listener;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * @author fan.zhang02
 * @date 2023/07/21/15:29
 */
@Slf4j
public class GrpcLogPrintServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        log.info("GrpcLogPrintServerInterceptor call...");
        long startTime = System.nanoTime();

        // 获取方法名
        MethodDescriptor<ReqT, RespT> methodDescriptor = call.getMethodDescriptor();
        String methodName = methodDescriptor.getFullMethodName();

        // 获取入参
        ServerCall.Listener<ReqT> listener = next.startCall(call, metadata);

        MonitorLogParams params = new MonitorLogParams();

        params.setServiceCls(GrpcService.class);
        params.setLogPoint(LogPoint.RPC_ENTRY);
        params.setTags(null);
        params.setService(call.getAuthority());
        params.setAction(methodName);
        params.setSuccess(true);
        params.setMsgCode("0");
        params.setMsgInfo("success");
        // 拦截响应
        ServerCall.Listener<ReqT> responseListener = new GrpcMonitorLogServerCall.SimpleForwardingServerCallListener<ReqT>(listener,
                Maps.newHashMap()) {
            @Override
            public void onMessage(ReqT message) {
                log.info("GrpcLogPrintServerInterceptor onMessage...");
                if (message instanceof MessageOrBuilder) {
                    //json序列化打印
                    try {
                        params.setInput(new Object[]{JsonFormat.printer().omittingInsignificantWhitespace()
                                .print((MessageOrBuilder) message)});
                    } catch (InvalidProtocolBufferException e) {
                        log.error("rpc sendMessage序列化成json错误", e);
                    }
                }
                // 这里可以对请求入参进行处理
                super.onMessage(message);
            }



            @Override
            public void onComplete() {
                log.info("GrpcLogPrintServerInterceptor onComplete...");
                // 请求完成时调用
                super.onComplete();
                long endTime = System.nanoTime();
                long elapsedTime = endTime - startTime;

                // 这里可以对执行耗时进行处理
                log.info("Method: " + methodName + " took " + elapsedTime + " nanoseconds");
            }
        };


        return responseListener;
    }
}
