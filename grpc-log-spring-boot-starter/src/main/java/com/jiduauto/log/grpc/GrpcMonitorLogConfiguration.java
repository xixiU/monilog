package com.jiduauto.log.grpc;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.jiduauto.log.core.LogParser;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.parse.ParsedResult;
import com.jiduauto.log.core.parse.ResultParseStrategy;
import com.jiduauto.log.core.util.*;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

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
@Configuration
@ConditionalOnProperty(prefix = "monitor.log.grpc", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('grpc')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('grpc'))")
@ConditionalOnClass(name = {"io.grpc.stub.AbstractStub", "io.grpc.stub.ServerCalls", "com.jiduauto.log.core.CoreMonitorLogConfiguration"})
@Slf4j
class GrpcMonitorLogConfiguration {
    static final String TIME_KEY = "nowTime";

    @GrpcGlobalServerInterceptor
    @Order(-100)
    @ConditionalOnProperty(prefix = "monitor.log.grpc.server", name = "enable", havingValue = "true", matchIfMissing = true)
    GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
        return new GrpcLogPrintServerInterceptor();
    }

    @GrpcGlobalClientInterceptor
    @Order(-101)
    @ConditionalOnClass(name = "io.grpc.ClientInterceptor")
    @ConditionalOnProperty(prefix = "monitor.log.grpc.client", name = "enable", havingValue = "true", matchIfMissing = true)
    GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
        return new GrpcLogPrintClientInterceptor();
    }

    @Slf4j
    static class GrpcLogPrintClientInterceptor implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel channel) {
            return new GrpcLogPrintClientInterceptor.GrpcMonitorLogClientCall<>(channel.newCall(method, callOptions), new ConcurrentHashMap<>(), method);
        }

        @Slf4j
        static class GrpcMonitorLogClientCall<ReqT, RespT> extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
            private final ConcurrentHashMap<String, Object> context;
            private final MonitorLogParams params;
            private final MethodDescriptor<ReqT, RespT> method;

            public GrpcMonitorLogClientCall(ClientCall<ReqT, RespT> delegate, ConcurrentHashMap<String, Object> context, MethodDescriptor<ReqT, RespT> method) {
                super(delegate);
                this.context = context == null ? new ConcurrentHashMap<>() : context;
                this.params = new MonitorLogParams();
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
                        context.put("logParser", logParser);
                    }
                } catch (Exception e) {
                }

                params.setServiceCls(serviceCls);
                params.setLogPoint(LogPoint.REMOTE_CLIENT);
                params.setService(serviceName);
                params.setAction(methodName);
                params.setSuccess(true);
                params.setMsgCode(ErrorEnum.SUCCESS.name());
                params.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                params.setTags(null);
                super.start(new GrpcLogPrintClientInterceptor.GrpcLogClientListener<>(responseListener, params, context), metadata);
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
        static class GrpcLogClientListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {
            private final MonitorLogParams params;

            private final ConcurrentHashMap<String, Object> context;

            protected GrpcLogClientListener(ClientCall.Listener<RespT> delegate, MonitorLogParams params, ConcurrentHashMap<String, Object> context) {
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
                            //尝试更精确的提取业务失败信息
                            ResultParseStrategy rps = cl == null ? null : cl.resultParseStrategy();//默认使用IfSuccess策略
                            String boolExpr = cl == null ? null : cl.boolExpr();
                            String codeExpr = cl == null ? null : cl.errorCodeExpr();
                            String msgExpr = cl == null ? null : cl.errorMsgExpr();
                            ParsedResult pr = ResultParseUtil.parseResult(json, rps, null, boolExpr, codeExpr, msgExpr);
                            params.setSuccess(pr.isSuccess());
                            params.setMsgCode(pr.getMsgCode());
                            params.setMsgInfo(pr.getMsgInfo());
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
                }
                super.onClose(status, trailers);
                MonitorLogUtil.log(params);
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
            ServerCall<ReqT, RespT> wrappedCall = new GrpcLogPrintServerInterceptor.WrappedServerCall<>(call, params, context);
            ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, metadata);
            return new GrpcLogPrintServerInterceptor.GrpcMonitorLogServerListener<>(listener, params, context);
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
                        ResultParseStrategy rps = cl == null ? null : cl.resultParseStrategy();//默认使用IfSuccess策略
                        String boolExpr = cl == null ? null : cl.boolExpr();
                        String codeExpr = cl == null ? null : cl.errorCodeExpr();
                        String msgExpr = cl == null ? null : cl.errorMsgExpr();
                        ParsedResult pr = ResultParseUtil.parseResult(json, rps, null, boolExpr, codeExpr, msgExpr);
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


    static long parseCostTime(Map<String, Object> context) {
        Long nowTime = (Long) context.get(TIME_KEY);
        long cost = 0;
        if (nowTime != null) {
            cost = System.currentTimeMillis() - nowTime;
        }
        return cost;
    }

    static String print2Json(MessageOrBuilder message) {
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(message);
        } catch (Exception e) {
            log.error("rpc message序列化成json错误", e);
            return message.toString();
        }
    }

    static Object tryConvert2Json(MessageOrBuilder message) {
        try {
            String json = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
            try {
                return JSON.parse(json);
            } catch (Exception ignore) {
                return json;
            }
        } catch (Exception e) {
            log.error("rpc message序列化成json错误", e);
            return message.toString();
        }
    }

    static String buildActionName(String fullMethodName, String serviceName) {
        return StringUtils.remove(StringUtils.removeStart(fullMethodName, serviceName), "/");
    }

    static Class<?> getCurrentProtoClass(MethodDescriptor<?, ?> method) {
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
