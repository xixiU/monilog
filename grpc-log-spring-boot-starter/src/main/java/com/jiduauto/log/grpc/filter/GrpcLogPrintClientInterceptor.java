package com.jiduauto.log.grpc.filter;

import com.google.protobuf.MessageOrBuilder;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.ExceptionUtil;
import com.jiduauto.log.core.util.MonitorLogUtil;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yp
 * @date 2023/07/25
 */
public class GrpcLogPrintClientInterceptor extends InterceptorHelper implements ClientInterceptor {
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new GrpcMonitorLogClientCall<>(next.newCall(method, callOptions), new ConcurrentHashMap<>(), method.getServiceName(), method.getFullMethodName());
    }

    @Slf4j
    static class GrpcMonitorLogClientCall<ReqT, RespT> extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
        private final ConcurrentHashMap<String, Object> context;
        private final MonitorLogParams params;
        private final String serviceName;
        private final String fullMethodName;

        public GrpcMonitorLogClientCall(ClientCall<ReqT, RespT> delegate, ConcurrentHashMap<String, Object> context, String serviceName, String fullMethodName) {
            super(delegate);
            this.context = context == null ? new ConcurrentHashMap<>() : context;
            this.params = new MonitorLogParams();
            this.serviceName = serviceName;
            this.fullMethodName = fullMethodName;
        }

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            params.setServiceCls(GrpcClient.class);//TODO
            params.setLogPoint(LogPoint.REMOTE_CLIENT);
            params.setService(serviceName);
            params.setAction(buildActionName(fullMethodName, serviceName));
            params.setSuccess(true);
            params.setMsgCode(ErrorEnum.SUCCESS.name());
            params.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            params.setTags(null);
            super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                @Override
                public void onMessage(RespT message) {
                    try {
                        if (message instanceof MessageOrBuilder) {
                            String json = print2Json((MessageOrBuilder) message);
                            params.setOutput(json);
                            //TODO 这里要解析响应码
                        }
                        super.onMessage(message);
                    } catch (Exception e) {
                        if (params.getException() == null) {
                            params.setException(e);
                        }
                        params.setSuccess(false);
                        params.setMsgCode(ErrorEnum.SYSTEM_ERROR.name());
                        params.setMsgInfo("Rpc响应异常:" + ExceptionUtil.getErrorMsg(e));
                        throw e;
                    } finally {
                        params.setCost(parseCostTime(context));
                        MonitorLogUtil.log(params);
                    }
                }

                @Override
                public void onClose(Status status, Metadata trailers) {
                    if (params.getCost() == 0) {
                        params.setCost(parseCostTime(context));
                    }
                }
            }, headers);
        }

        @Override
        public void sendMessage(ReqT message) {
            if (message instanceof MessageOrBuilder) {
                params.setInput(new Object[]{print2Json((MessageOrBuilder) message)});
            }
            if (!context.containsKey(TIME_KEY)) {
                context.put(TIME_KEY, System.currentTimeMillis());
            }
            try {
                super.sendMessage(message);
            } catch (Throwable t) {
                params.setSuccess(false);
                params.setException(t);
                params.setMsgCode(ErrorEnum.SYSTEM_ERROR.name());
                params.setMsgInfo("Rpc调用异常:" + ExceptionUtil.getErrorMsg(t));
            }
        }

        @Override
        public void request(int numMessages) {
            super.request(numMessages);
            context.put(TIME_KEY, System.currentTimeMillis());
        }
    }
}

