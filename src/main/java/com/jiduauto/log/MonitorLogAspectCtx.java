package com.jiduauto.log;

import com.google.common.base.Preconditions;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yp
 */
@Getter
class MonitorLogAspectCtx {
    private static final Map<Class<?>, Logger> LOGGERS = new HashMap<>();
    private static final Map<Method, Class<?>> METHOD_CLS_CACHE = new HashMap<>();
    private final Method method;
    private final Object[] args;
    private final LogPoint logPoint;
    private final LogParser logParserAnnotation;
    private final Class<?> methodOwnedClass;
    private long cost;
    private Object result;
    private Throwable exception;
    /**
     * 根据响响应结果，解析到的"success/msgCode/msgInfo"三元组
     */
    private ParsedResult parsedResult;

    public MonitorLogAspectCtx(ProceedingJoinPoint pjp, Object[] args) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Preconditions.checkNotNull(method, "aspect方法为空");
        this.method = method;
        this.args = args;
        //找到指定方法的所属的接口，如果找不到接口，则返回方法的所属类
        this.methodOwnedClass = getMethodCls(method);
        Method targetMethod = null;
        boolean isAbstract = Modifier.isAbstract(method.getModifiers());
        if (isAbstract) {
            try {
                targetMethod = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ignore) {
            }
        }
        //找到方法上的ClientLog注解，如果找不到则向上找类上的，如果还找不到，则再向上找接口上的
        this.logParserAnnotation = getAnnotation(LogParser.class, targetMethod, method);
        MonitorLog anno = getAnnotation(MonitorLog.class, targetMethod, method);
        this.logPoint = anno == null ? LogPoint.UNKNOWN_ENTRY : anno.value();
    }

    private <T extends Annotation> T getAnnotation(Class<T> annotationClass, Method... methods) {
        assert methods != null && methods.length > 0;
        T annotation = null;
        //方法上的
        for (Method method : methods) {
            annotation = method == null ? null : method.getAnnotation(annotationClass);
            if (annotation != null) {
                break;
            }
        }
        if (annotation != null) {
            return annotation;
        }
        //类上的
        for (Method method : methods) {
            annotation = method == null ? null : method.getDeclaringClass().getAnnotation(annotationClass);
            if (annotation != null) {
                break;
            }
        }
        if (annotation != null) {
            return annotation;
        }
        //接口上的
        for (Method method : methods) {
            annotation = method == null ? null : methodOwnedClass.getAnnotation(annotationClass);
            if (annotation != null) {
                break;
            }
        }
        return annotation;
    }

    public MonitorLogAspectCtx buildResult(long cost, Object result, Throwable exception) {
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

    @SuppressWarnings("unchecked")
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
        return logParserAnnotation != null && StringUtils.isNotBlank(logParserAnnotation.serviceName())
                ? logParserAnnotation.serviceName() : StringUtils.uncapitalize(cls.getSimpleName());
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
