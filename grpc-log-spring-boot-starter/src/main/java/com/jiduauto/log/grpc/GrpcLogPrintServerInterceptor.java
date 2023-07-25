package com.jiduauto.log.grpc;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.MessageOrBuilder;
import com.jiduauto.log.core.LogParser;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.parse.ParsedResult;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.jiduauto.log.core.util.ReflectUtil;
import com.jiduauto.log.core.util.ResultParseUtil;
import io.grpc.*;
import io.grpc.ServerCall.Listener;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author fan.zhang02
 * @date 2023/07/21/15:29
 */
@Slf4j
class GrpcLogPrintServerInterceptor extends InterceptorHelper implements ServerInterceptor {

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        MethodDescriptor<ReqT, RespT> method = call.getMethodDescriptor();
        Map<String, Object> context = new ConcurrentHashMap<>();
        Class<?> cls = getCurrentProtoClass(method);
        StackTraceElement ste = getNextClassFromStack(cls);
        Class<?> serviceCls = GrpcService.class;
        String serviceName = method.getServiceName();
        String methodName = buildActionName(method.getFullMethodName(), serviceName);
        try {
            if (ste != null) {
                serviceCls = Class.forName(ste.getClassName());
                serviceName = serviceCls.getSimpleName();
                methodName = ste.getMethodName();
                List<Method> list = Arrays.stream(serviceCls.getMethods()).filter(e -> ste.getMethodName().equals(e.getName())).collect(Collectors.toList());
                Method[] array = list.toArray(new Method[]{});
                LogParser logParser = ReflectUtil.getAnnotation(LogParser.class, serviceCls, array);
                context.put("logParser", logParser);
            }
        } catch (Exception e) {
        }


        MonitorLogParams params = new MonitorLogParams();
        params.setServiceCls(serviceCls);
        params.setLogPoint(LogPoint.RPC_ENTRY);
        params.setTags(null);
        params.setService(serviceName);
        params.setAction(methodName);
        params.setSuccess(true);
        params.setMsgCode(ErrorEnum.SUCCESS.name());
        params.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
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
        public void sendMessage(RespT message) {
            if (message instanceof MessageOrBuilder) {
                Object json = tryConvert2Json((MessageOrBuilder) message);
                params.setOutput(json);
                LogParser cl = (LogParser) context.get("logParser");
                if (json instanceof JSON && cl != null) {
                    //尝试更精确的提取业务失败信息
                    ParsedResult pr = ResultParseUtil.parseResult(json, cl.resultParseStrategy(), null, cl.boolExpr(), cl.errorCodeExpr(), cl.errorMsgExpr());
                    params.setSuccess(pr.isSuccess());
                    params.setMsgCode(pr.getMsgCode());
                    params.setMsgInfo(pr.getMsgInfo());
                }
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
            context.put(TIME_KEY, System.currentTimeMillis());
            if (message instanceof MessageOrBuilder) {
                params.setInput(new Object[]{print2Json((MessageOrBuilder) message)});
            }
            super.onMessage(message);
        }

        @Override
        public void onComplete() {
            super.onComplete();
            params.setCost(parseCostTime(context));
            MonitorLogUtil.log(params);
        }
    }
}
