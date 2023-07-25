package com.jiduauto.log.grpc.filter;

import com.google.protobuf.MessageOrBuilder;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import io.grpc.*;
import io.grpc.ServerCall.Listener;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author fan.zhang02
 * @date 2023/07/21/15:29
 */
@Slf4j
public class GrpcLogPrintServerInterceptor extends InterceptorHelper implements ServerInterceptor {

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        MethodDescriptor<ReqT, RespT> methodDescriptor = call.getMethodDescriptor();
        MonitorLogParams params = new MonitorLogParams();
        params.setServiceCls(GrpcService.class);
        params.setLogPoint(LogPoint.RPC_ENTRY);
        params.setTags(null);
        params.setService(methodDescriptor.getServiceName());
        params.setAction(buildActionName(methodDescriptor.getFullMethodName(), methodDescriptor.getServiceName()));
        params.setSuccess(true);
        params.setMsgCode(ErrorEnum.SUCCESS.name());
        params.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
        // 拦截响应
        Map<String, Object> context = new ConcurrentHashMap<>();
        return new GrpcMonitorLogServerCall<>(next.startCall(call, metadata), params, context);
    }


    static class GrpcMonitorLogServerCall<ReqT> extends ForwardingServerCallListener<ReqT> {
        private final MonitorLogParams params;
        private final Map<String, Object> context;
        private final ServerCall.Listener<ReqT> delegate;

        protected GrpcMonitorLogServerCall(ServerCall.Listener<ReqT> delegate, MonitorLogParams params, Map<String, Object> context) {
            this.delegate = delegate;
            this.params = params;
            this.context = context == null ? new ConcurrentHashMap<>() : context;
        }

        @Override
        protected Listener<ReqT> delegate() {
            return delegate;
        }

        @Override
        public void onMessage(ReqT message) {
            log.info("GrpcLogPrintServerInterceptor onMessage...");
            context.put("nowTime", System.currentTimeMillis());
            if (message instanceof MessageOrBuilder) {
                params.setInput(new Object[]{print2Json((MessageOrBuilder) message)});
            }
            super.onMessage(message);
        }


        @Override
        public void onComplete() {
            log.info("GrpcLogPrintServerInterceptor onComplete...");
            super.onComplete();
            long startTime = (Long) context.get("nowTime");
            long cost = System.currentTimeMillis() - startTime;
            params.setCost(cost);
        }

        @Override
        public void onHalfClose() {
            log.info("GrpcLogPrintServerInterceptor onHalfClose...");
            super.onHalfClose();
        }

        @Override
        public void onCancel() {
            log.info("GrpcLogPrintServerInterceptor onCancel...");
            super.onCancel();
        }

        @Override
        public void onReady() {
            log.info("GrpcLogPrintServerInterceptor onReady...");
            super.onReady();
        }
    }
}
