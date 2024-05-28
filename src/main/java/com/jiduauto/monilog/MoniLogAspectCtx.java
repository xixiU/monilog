package com.jiduauto.monilog;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yp
 */
@Getter
@Slf4j
class MoniLogAspectCtx {
    private static final Map<Class<?>, Logger> LOGGERS = new HashMap<>();
    private static final Map<Method, Class<?>> METHOD_CLS_CACHE = new HashMap<>();
    private final Method method;
    private final Object[] args;
    private final Class<?> methodOwnedClass;
    @Setter
    private LogParser logParserAnnotation;
    @Setter
    private LogPoint logPoint;
    private boolean hasExecuted;
    private long cost;
    private Object result;
    private Throwable exception;
    /**
     * 指标tag
     */
    private final String[] tags;

    /**
     * 指标名称
     */
    private final String metricName;

    /**
     * 根据响响应结果，解析到的"success/msgCode/msgInfo"三元组
     */
    private ParsedResult parsedResult;

    public MoniLogAspectCtx(MoniLogAop.InvocationProxy proxy) {
        this.method = proxy.getMethod();
        Preconditions.checkNotNull(method, "aspect方法为空");
        this.args = proxy.getArgs();
        //找到指定方法的所属的接口，如果找不到接口，则返回方法的所属类
        this.methodOwnedClass = getMethodCls(method);
        Method targetMethod = null;
        boolean isAbstract = Modifier.isAbstract(method.getModifiers());
        if (isAbstract) {
            try {
                targetMethod = proxy.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ignore) {
            }
        }
        //找到方法上的注解，如果找不到则向上找类上的，如果还找不到，则再向上找接口上的
        this.logParserAnnotation = ReflectUtil.getAnnotation(LogParser.class, methodOwnedClass, targetMethod, method);
        MoniLog anno = ReflectUtil.getAnnotation(MoniLog.class, methodOwnedClass, targetMethod, method);
        MoniLogTags logTags = ReflectUtil.getAnnotation(MoniLogTags.class, methodOwnedClass, targetMethod, method);
        this.logPoint = anno == null ? LogPoint.unknown : anno.value();
        this.tags = StringUtil.getTagArray(logTags);
        this.metricName = logTags!= null ? logTags.metricName() : "";
    }

    public MoniLogAspectCtx buildResult(long cost, Object result, Throwable exception) {
        this.hasExecuted = true;
        this.cost = cost;
        this.result = result;
        this.exception = exception;
        LogParser cl = this.logParserAnnotation;
        ResultParseStrategy rps = cl == null ? null : cl.resultParseStrategy();
        String boolExpr = cl == null ? null : cl.boolExpr();
        String codeExpr = cl == null ? null : cl.errorCodeExpr();
        String msgExpr = cl == null ? null : cl.errorMsgExpr();
        this.parsedResult = ResultParseUtil.parseResult(result, rps, exception, boolExpr, codeExpr, msgExpr);
        return this;
    }

    public String getMethodName() {
        return method.getName();
    }

    @SuppressWarnings("all")
    public <T> T getArgByType(Class<T> cls) {
        if (args == null || args.length == 0) {
            return null;
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (cls.isAssignableFrom(arg.getClass())) {
                return (T) arg;
            }
        }
        return null;
    }

    public Object getFirstArg() {
        if (args == null || args.length == 0) {
            return null;
        }
        return args[0];
    }

    public Object getArgByIndex(int index) {
        if (args == null || args.length == 0 || index < 0 || index >= args.length) {
            return null;
        }
        return args[index];
    }

    public String parseServiceName() {
        Class<?> cls = this.getMethodOwnedClass();
        if (logParserAnnotation != null && StringUtils.isNotBlank(logParserAnnotation.serviceName())) {
            return logParserAnnotation.serviceName();
        }
        return ReflectUtil.getSimpleClassName(cls);
    }

    public Logger getLogger() {
        //这里一定要取调用者对应的类，不然日志中输出的类名就都是当前SystemAspect这个类了
        Class<?> cls = method.getDeclaringClass();
        if (LOGGERS.containsKey(cls)) {
            return LOGGERS.get(cls);
        }
        Logger logger = LoggerFactory.getLogger(cls);
        LOGGERS.put(cls, logger);
        return logger;
    }

    /**
     * 找到指定方法的所属的接口，如果找不到接口，则返回方法的所属类
     */
    private static Class<?> getMethodCls(Method method) {
        Class<?> cls = METHOD_CLS_CACHE.get(method);
        if (cls != null) {
            return cls;
        }
        cls = ReflectUtil.getInterfaceByGivenMethod(method);
        if (cls == null) {
            cls = method.getDeclaringClass();
        }
        METHOD_CLS_CACHE.put(method, cls);
        return cls;
    }
}
