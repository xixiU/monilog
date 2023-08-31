package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author dianming.cao
 * @date 2022/8/16
 */

@Slf4j
class GrpcMoniLogInterceptor {
    private static final String TIME_KEY = "nowTime";
    @Slf4j
    static class GrpcLogPrintClientInterceptor implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel channel) {
            return new GrpcMoniLogClientCall<>(channel.newCall(method, callOptions), new ConcurrentHashMap<>(), method);
        }

        @Slf4j
        private static class GrpcMoniLogClientCall<ReqT, RespT> extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
            private final ConcurrentHashMap<String, Object> context;
            private final MoniLogParams params;
            private final MethodDescriptor<ReqT, RespT> method;

            public GrpcMoniLogClientCall(ClientCall<ReqT, RespT> delegate, ConcurrentHashMap<String, Object> context, MethodDescriptor<ReqT, RespT> method) {
                super(delegate);
                this.context = context == null ? new ConcurrentHashMap<>() : context;
                this.params = new MoniLogParams();
                this.method = method;
            }

            @Override
            public void start(Listener<RespT> responseListener, Metadata metadata) {
                Class<?> cls = getCurrentProtoClass(method);
                StackTraceElement ste = ThreadUtil.getNextClassFromStack(cls);
                Class<?> serviceCls = GrpcClient.class;
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
                        if (logParser != null) {
                            context.put("logParser", logParser);
                        }
                    }
                } catch (Exception e) {
                    MoniLogUtil.innerDebug("GrpcMoniLogClientCall.start error", e);
                }

                params.setServiceCls(serviceCls);
                params.setLogPoint(LogPoint.grpc_client);
                params.setService(serviceName);
                params.setAction(methodName);
                params.setSuccess(true);
                params.setMsgCode(ErrorEnum.SUCCESS.name());
                params.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                params.setTags(null);
                super.start(new GrpcLogClientListener<>(responseListener, params, context), metadata);
            }

            @Override
            public void sendMessage(ReqT message) {
                if (!context.containsKey(TIME_KEY)) {
                    context.put(TIME_KEY, System.currentTimeMillis());
                }
                if (message instanceof MessageOrBuilder) {
                    params.setInput(new Object[]{tryConvert2Json((MessageOrBuilder) message)});
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
                if (!context.containsKey(TIME_KEY)) {
                    context.put(TIME_KEY, System.currentTimeMillis());
                }
            }
        }

        @Slf4j
        private static class GrpcLogClientListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
            private final MoniLogParams params;

            private final ConcurrentHashMap<String, Object> context;

            protected GrpcLogClientListener(ClientCall.Listener<RespT> delegate, MoniLogParams params, ConcurrentHashMap<String, Object> context) {
                super(delegate);
                this.params = params;
                this.context = context;
            }

            @Override
            public void onMessage(RespT message) {
                try {
                    if (message instanceof MessageOrBuilder) {
                        Object json = tryConvert2Json((MessageOrBuilder) message);
                        params.setOutput(json);
                        LogParser cl = (LogParser) context.get("logParser");
                        if (json instanceof JSON) {
                            ResultParseUtil.parseResultAndSet(cl, (JSON)json, params);
                        }
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
                }
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
                if (params.getCost() == 0) {
                    params.setCost(parseCostTime(context));
                }
                if (!status.isOk()) {
                    params.setSuccess(false);
                    params.setMsgInfo(status.getDescription());
                    params.setMsgCode(status.getCode().name());
                    params.setException(new StatusRuntimeException(status, trailers));
                }
                super.onClose(status, trailers);
                MoniLogUtil.log(params);
            }
        }
    }

    @Slf4j
    static class GrpcLogPrintServerInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
            MethodDescriptor<ReqT, RespT> method = call.getMethodDescriptor();
            Map<String, Object> context = new ConcurrentHashMap<>();
            Class<?> cls = getCurrentProtoClass(method);
            StackTraceElement ste = ThreadUtil.getNextClassFromStack(cls);
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
                    if (logParser != null) {
                        context.put("logParser", logParser);
                    }
                }
            } catch (Exception e) {
                MoniLogUtil.innerDebug("GrpcLogPrintServerInterceptor.interceptCall error", e);
            }


            MoniLogParams params = new MoniLogParams();
            params.setServiceCls(serviceCls);
            params.setLogPoint(LogPoint.grpc_server);
            params.setTags(null);
            params.setService(serviceName);
            params.setAction(methodName);
            params.setSuccess(true);
            params.setMsgCode(ErrorEnum.SUCCESS.name());
            params.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            ServerCall<ReqT, RespT> wrappedCall = new WrappedServerCall<>(call, params, context);
            ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, metadata);
            return new GrpcMoniLogServerListener<>(listener, params, context);
        }


        private static class WrappedServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
            private final MoniLogParams params;
            private final Map<String, Object> context;

            public WrappedServerCall(ServerCall<ReqT, RespT> delegate, MoniLogParams params, Map<String, Object> context) {
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
                        ResultParseUtil.parseResultAndSet(cl, (JSON)json, params);
                    }
                }
                super.sendMessage(message);
            }
        }


        private static class GrpcMoniLogServerListener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
            private final MoniLogParams params;
            private final Map<String, Object> context;

            protected GrpcMoniLogServerListener(ServerCall.Listener<ReqT> delegate, MoniLogParams params, Map<String, Object> context) {
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
                MoniLogUtil.log(params);
            }
        }
    }


    private static long parseCostTime(Map<String, Object> context) {
        Long nowTime = (Long) context.get(TIME_KEY);
        long cost = 0;
        if (nowTime != null) {
            cost = System.currentTimeMillis() - nowTime;
        }
        return cost;
    }

    private static String print2Json(MessageOrBuilder message) {
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(message);
        } catch (Exception e) {
            MoniLogUtil.innerDebug("rpc message序列化成json错误", e);
            return message.toString();
        }
    }

    private static Object tryConvert2Json(MessageOrBuilder message) {
        try {
            String json = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
            try {
                return JSON.parse(json);
            } catch (Exception ignore) {
                return json;
            }
        } catch (Exception e) {
            MoniLogUtil.innerDebug("rpc message序列化成json错误", e);
            return message.toString();
        }
    }

    private static String buildActionName(String fullMethodName, String serviceName) {
        return StringUtils.remove(StringUtils.removeStart(fullMethodName, serviceName), "/");
    }

    private static Class<?> getCurrentProtoClass(MethodDescriptor<?, ?> method) {
        Class<?> cls = null;
        if (method.getSchemaDescriptor() != null) {
            cls = method.getSchemaDescriptor().getClass();
        }
        if (cls != null && cls.isMemberClass()) {
            cls = cls.getDeclaringClass();
        }
        return cls;
    }
}
