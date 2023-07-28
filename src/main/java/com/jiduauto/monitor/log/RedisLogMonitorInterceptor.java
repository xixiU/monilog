package com.jiduauto.monitor.log;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Slf4j
class RedisLogMonitorInterceptor {

    @Aspect
    @AllArgsConstructor
    static class RedisTemplateEnhancer {
        private final MonitorLogProperties.RedisProperties redis;
        @Around("execution(public * *.execute(org.springframework.data.redis.core.RedisCallback, boolean ,boolean))")
        Object intercept(ProceedingJoinPoint pjp) {
            long start = System.currentTimeMillis();
            long cost = 0;
            Throwable ex = null;
            Object ret = null;
            try {
                ret = pjp.proceed();
            } catch (Throwable e) {
                ex = e;
            } finally {
                cost = System.currentTimeMillis() - start;
            }
            log.info("redis monitor execute... to be implemented");

            MonitorLogParams p = new MonitorLogParams();
            p.setServiceCls(null);
            p.setService("");
            p.setAction("");
            p.setTags(new String[]{});

            p.setCost(cost);
            p.setException(ex);
            p.setSuccess(true);
            p.setLogPoint(LogPoint.redis);
            p.setInput(new Object[]{});
            p.setMsgCode(ErrorEnum.SUCCESS.name());
            p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            if (ex != null) {
                ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
                if (errorInfo != null) {
                    p.setMsgCode(errorInfo.getErrorCode());
                    p.setMsgInfo(errorInfo.getErrorMsg());
                }
            }
            //after
            return ret;
        }
    }
}