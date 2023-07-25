package com.jiduauto.log.grpclogspringbootstarter.filter;

import com.jiduauto.log.enums.LogPoint;
import com.jiduauto.log.model.MonitorLogParams;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * @author fan.zhang02
 * @date 2023/07/21/15:29
 */
@Slf4j
public class GrpcLogPrintServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadata,
            ServerCallHandler<ReqT, RespT> next) {
        long startTime = System.nanoTime();

        // 获取方法名
        MethodDescriptor<ReqT, RespT> methodDescriptor = call.getMethodDescriptor();
        String methodName = methodDescriptor.getFullMethodName();

        // 获取入参
        ServerCall.Listener<ReqT> listener = next.startCall(call, metadata);

        MonitorLogParams params = new MonitorLogParams();

        params.setServiceCls(GrpcClient.class);
        params.setLogPoint(LogPoint.RPC_ENTRY);
        params.setTags(null);
//        params.setService(next.);
        params.setAction(methodName);
        params.setSuccess(true);
        params.setMsgCode("0");
        params.setMsgInfo("success");

        // 拦截响应
        ServerCall.Listener<ReqT> responseListener = new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {

            @Override
            public void onMessage(ReqT message) {
                // 这里可以对请求入参进行处理
                super.onMessage(message);
            }

            @Override
            public void onComplete() {
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
