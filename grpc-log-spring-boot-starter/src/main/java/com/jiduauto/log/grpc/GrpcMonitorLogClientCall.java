package com.jiduauto.log.grpc;

import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;

import java.util.Map;

/**
 * @author fan.zhang02
 * @date 2023/07/24/19:19
 */
public class GrpcMonitorLogClientCall<ReqT, RespT> extends SimpleForwardingClientCall<ReqT, RespT> {
    private Map<String, Object> context = null;

    public GrpcMonitorLogClientCall(ClientCall delegate, Map<String, Object> context) {
        super(delegate);
        this.context = context;
    }

    public Map<String, Object> getContext() {
        return context;
    }
}
