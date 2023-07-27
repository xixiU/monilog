package com.jiduauto.monitor.log;


import com.jiduauto.monitor.log.enums.ErrorEnum;
import com.jiduauto.monitor.log.enums.LogPoint;
import com.jiduauto.monitor.log.model.ErrorInfo;
import com.jiduauto.monitor.log.model.MonitorLogParams;
import com.jiduauto.monitor.log.util.ExceptionUtil;
import com.jiduauto.monitor.log.util.MonitorLogUtil;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.lang.reflect.Method;

@Configuration
@ConditionalOnProperty(prefix = "monitor.log.xxljob", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('xxljob')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('xxljob'))")
@ConditionalOnClass({IJobHandler.class, CoreMonitorLogConfiguration.class})
@ConditionalOnBean(MonitorLogPrinter.class)
@AutoConfigureAfter(CoreMonitorLogConfiguration.class)
class XxlJobMonitorLogConfiguration {
    @Bean
    XxlJobLogMonitorExecuteInterceptor xxlJobExecuteInterceptor() {
        return new XxlJobLogMonitorExecuteInterceptor();
    }

    @Slf4j
    @Aspect
    static class XxlJobLogMonitorExecuteInterceptor implements BeanPostProcessor, Ordered {
        //处理基于@XxlJob注解的形式
        @Around("@annotation(com.xxl.job.core.handler.annotation.XxlJob)")
        Object doAround(ProceedingJoinPoint pjp) throws Throwable {
            Object[] args = pjp.getArgs();
            Object target = pjp.getTarget();
            Method method = ((MethodSignature) pjp.getSignature()).getMethod();
            return new TaskProxy<>(target.getClass(), method.getName(), args).doAround(e -> (ReturnT<Object>) pjp.proceed(e));
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof IJobHandler && !(bean instanceof EnhancedJobHandler)) {
                return new EnhancedJobHandler((IJobHandler) bean);
            }
            return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
        }

        @Override
        public int getOrder() {
            return Integer.MAX_VALUE;
        }
    }


    @AllArgsConstructor
    static class EnhancedJobHandler extends IJobHandler {
        private final IJobHandler handler;

        //处理基于实现IJobHandler父类的形式
        @SneakyThrows
        @Override
        public ReturnT<String> execute(String param) throws Exception {
            TaskInvoker<String> invoker = args -> handler.execute(args != null && args.length > 0 ? (String) args[0] : null);
            return new TaskProxy<String>(handler.getClass(), "execute", new Object[]{param}).doAround(invoker);
        }
    }

    @AllArgsConstructor
    static class TaskProxy<T> {
        private Class<?> cls;
        private String method;
        private Object[] args;

        ReturnT<T> doAround(TaskInvoker<T> invoker) throws Throwable {
            long start = System.currentTimeMillis();
            MonitorLogParams params = new MonitorLogParams();
            params.setLogPoint(LogPoint.xxljob);
            params.setServiceCls(cls);
            params.setService(cls.getSimpleName());
            params.setAction(method);
            params.setInput(args);
            params.setSuccess(true);
            params.setMsgCode(ErrorEnum.SUCCESS.name());
            params.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            try {
                ReturnT<T> ret = invoker.invoke(args);
                params.setSuccess(ret.getCode() == ReturnT.SUCCESS_CODE);
                params.setMsgCode(String.valueOf(ret.getCode()));
                params.setMsgInfo(ret.getMsg());
                params.setOutput(ret.getContent());
                return ret;
            } catch (Exception ex) {
                params.setException(ex);
                ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
                params.setSuccess(false);
                params.setMsgCode(errorInfo.getErrorCode());
                params.setMsgInfo(errorInfo.getErrorMsg());
                throw ex;
            } finally {
                params.setCost(System.currentTimeMillis() - start);
                MonitorLogUtil.log(params);
            }
        }
    }

    @FunctionalInterface
    interface TaskInvoker<T> {
        ReturnT<T> invoke(Object[] args) throws Throwable;
    }
}
