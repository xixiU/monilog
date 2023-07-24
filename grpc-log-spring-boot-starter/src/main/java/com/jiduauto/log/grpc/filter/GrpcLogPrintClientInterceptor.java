package com.jiduauto.log.grpc.filter;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;

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
        return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            MonitorLogParams params = new MonitorLogParams();

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {

                params.setServiceCls(GrpcClient.class);
                params.setLogPoint(LogPoint.RPC_ENTRY);
                params.setTags(null);
                params.setService(next.authority());
                params.setAction(method.getFullMethodName());
                params.setSuccess(true);
                params.setMsgCode("0");
                params.setMsgInfo("success");


                super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onMessage(RespT message) {
                        if (message instanceof MessageOrBuilder) {
                            try {
                                params.setOutput(JsonFormat.printer().omittingInsignificantWhitespace()
                                        .print((MessageOrBuilder) message));
                            } catch (InvalidProtocolBufferException e) {
                                log.error("rpc onMessage序列化成json错误", e);
                            } finally {
                                MonitorLogUtil.log(params);
                            }
                        }
                        super.onMessage(message);
                    }
                }, headers);

            }

            @Override
            public void sendMessage(ReqT message) {
                if (message instanceof MessageOrBuilder) {
                    //json序列化打印
                    try {
                        params.setInput(new Object[]{JsonFormat.printer().omittingInsignificantWhitespace()
                                .print((MessageOrBuilder) message)});
                    } catch (InvalidProtocolBufferException e) {
                        log.error("rpc sendMessage序列化成json错误", e);
                    }
                }
                long nowTime = System.currentTimeMillis();
                try {
                    super.sendMessage(message);
                } catch (Throwable t) {
                    params.setSuccess(false);
                    params.setException(t);
                    params.setMsgCode("1");
                    params.setMsgInfo("fail");
                } finally {
                    params.setCost(System.currentTimeMillis() - nowTime);
                }

            }


        };
    }
}
