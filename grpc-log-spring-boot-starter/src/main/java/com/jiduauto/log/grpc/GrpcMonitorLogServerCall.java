package com.jiduauto.log.grpc;

import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;

import java.util.Map;

/**
 * @author fan.zhang02
 * @date 2023/07/24/19:19
 */
public abstract class GrpcMonitorLogServerCall<ReqT, RespT> extends ForwardingServerCallListener<ReqT> {
    public GrpcMonitorLogServerCall() {

    }
    @Override
    protected abstract ServerCall.Listener<ReqT> delegate();

    public void onMessage(ReqT message) {
        this.delegate().onMessage(message);
    }

    public abstract static class SimpleForwardingServerCallListener<ReqT> extends ForwardingServerCallListener<ReqT> {
        private Map<String, Object> context = null;
        private final ServerCall.Listener<ReqT> delegate;

        protected SimpleForwardingServerCallListener(ServerCall.Listener<ReqT> delegate) {
            this.delegate = delegate;
        }

        protected SimpleForwardingServerCallListener(ServerCall.Listener<ReqT> delegate, Map<String, Object> context) {
            this.delegate = delegate;
            this.context = context;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        protected ServerCall.Listener<ReqT> delegate() {
            return this.delegate;
        }
    }
}
