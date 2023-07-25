package com.jiduauto.log.grpc.filter;

import com.google.protobuf.MessageOrBuilder;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
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
        Map<String, Object> context = new ConcurrentHashMap<>();
        ServerCall<ReqT, RespT> wrappedCall = new WrappedServerCall<>(call, params, context);
        Listener<ReqT> listener = next.startCall(wrappedCall, metadata);
        return new GrpcMonitorLogServerListener<>(listener, params, context);
    }


    static class WrappedServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
        private final MonitorLogParams params;
        private final Map<String, Object> context;

        public WrappedServerCall(ServerCall<ReqT, RespT> delegate, MonitorLogParams params, Map<String, Object> context) {
            super(delegate);
            this.params = params;
            this.context = context;
        }

        @Override
        public void sendHeaders(Metadata headers) {
            log.info("GrpcLogPrintServerInterceptor sendHeaders...");
            if (!context.containsKey(TIME_KEY)) {
                context.put(TIME_KEY, System.currentTimeMillis());
            }
            super.sendHeaders(headers);
        }

        @Override
        public void sendMessage(RespT message) {
            log.info("GrpcLogPrintServerInterceptor sendMessage...");
            if (message instanceof MessageOrBuilder) {
                //TODO 这里要解析响应码
                params.setOutput(print2Json((MessageOrBuilder) message));
            }
            super.sendMessage(message);
        }
    }


    static class GrpcMonitorLogServerListener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
        private final MonitorLogParams params;
        private final Map<String, Object> context;

        protected GrpcMonitorLogServerListener(ServerCall.Listener<ReqT> delegate, MonitorLogParams params, Map<String, Object> context) {
            super(delegate);
            this.params = params;
            this.context = context == null ? new ConcurrentHashMap<>() : context;
        }

        @Override
        public void onMessage(ReqT message) {
            log.info("GrpcLogPrintServerInterceptor onMessage...");
            context.put(TIME_KEY, System.currentTimeMillis());
            if (message instanceof MessageOrBuilder) {
                params.setInput(new Object[]{print2Json((MessageOrBuilder) message)});
            }
            super.onMessage(message);
        }


        @Override
        public void onComplete() {
            log.info("GrpcLogPrintServerInterceptor onComplete...");
            super.onComplete();
            params.setCost(parseCostTime(context));
            MonitorLogUtil.log(params);
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
