package com.jiduauto.monilog;


import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Method;


@Slf4j
class RedisMoniLogInterceptor {
    //参考：https://github.com/youbl/study/blob/master/demo-log-redis/src/main/java/beinet/cn/logdemoredis/redis/RedisFactoryBean.java
    //注意spring的处理顺序是：
    //  1[TestProcessor]BeanFactoryAware.setBeanFactory...
    //  2[TestProcessor]ApplicationContextAware.setApplicationContext...
    //  3[TestProcessor]BeanDefinitionRegistryPostProcessor.postProcessBeanDefinitionRegistry...
    //  4[TestProcessor]BeanFactoryPostProcessor.postProcessBeanFactory...
    //  5[TestProcessor]InstantiationAwareBeanPostProcessor.postProcessBeforeInstantiation...
    //  6[TestProcessor]InstantiationAwareBeanPostProcessor.postProcessAfterInstantiation...
    //  7[TestProcessor]InstantiationAwareBeanPostProcessor.postProcessProperties...
    //  8[TestProcessor]BeanPostProcessor.postProcessBeforeInitialization...
    //  9[TestBean]InitializingBean.afterPropertiesSet...
    //  10[TestProcessor]BeanPostProcessor.postProcessAfterInitialization...
    //  11[TestBean]SmartInitializingSingleton.afterSingletonsInstantiated...
    protected static RedisConnectionFactory getProxyBean(Object bean) {
        return (RedisConnectionFactory) ProxyUtils.getProxy(bean, invocation -> {
            Object ret = invocation.proceed();
            String methodName = invocation.getMethod().getName();
            if (methodName.equals("getConnection")) {
                //如果是getConnection方法，把返回结果进行代理包装：在返回结果前后做一些额外的事情
                return ProxyUtils.getProxy(ret, RedisMoniLogInterceptor::doIntercept);
            }
            return ret;
        });
    }

    private static Object doIntercept(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        String methodName = method.getName();
        if (methodName.equals("isPipelined") || methodName.equals("close")) {
            return invocation.proceed();
        }
        Object target = invocation.getThis();
        String serviceName = target.getClass().getSimpleName();
        Object[] args = invocation.getArguments();
        log.info("redis monilog execute... to be implemented");

        MoniLogParams p = new MoniLogParams();
        p.setServiceCls(target.getClass());
        p.setService(serviceName);
        p.setAction(methodName);
        p.setSuccess(true);
        p.setLogPoint(LogPoint.redis);
        p.setMsgCode(ErrorEnum.SUCCESS.name());
        p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
        p.setTags(new String[]{});
        p.setInput(deserialize(args));
        long start = System.currentTimeMillis();
        try {
            Object ret = invocation.proceed();
            p.setOutput(deserialize(ret));
            return ret;
        } catch (Throwable e) {
            p.setException(e);
            p.setSuccess(false);
            ErrorInfo errorInfo = ExceptionUtil.parseException(e);
            p.setMsgCode(errorInfo.getErrorCode());
            p.setMsgInfo(errorInfo.getErrorMsg());
            throw e;
        } finally {
            p.setCost(System.currentTimeMillis() - start);
            MoniLogUtil.log(p);
        }
    }

    private static Object[] deserialize(Object... args) {
        return args;
    }
}