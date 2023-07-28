package com.jiduauto.monitor.log;


import com.jiduauto.monitor.log.aop.MonitorLogAop;
import com.jiduauto.monitor.log.enums.LogPoint;
import com.jiduauto.monitor.log.parse.LogParser;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.IJobHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "monitor.log.xxljob", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('xxljob')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('xxljob'))")
@ConditionalOnClass({IJobHandler.class, CoreMonitorLogConfiguration.class})
@ConditionalOnBean(MonitorLogPrinter.class)
@AutoConfigureAfter(CoreMonitorLogConfiguration.class)
class XxlJobMonitorLogConfiguration {
    @Bean
    public XxlJobLogMonitorExecuteInterceptor xxlJobExecuteInterceptor() {
        return new XxlJobLogMonitorExecuteInterceptor();
    }

    @Aspect
    static class XxlJobLogMonitorExecuteInterceptor {
        @Around("execution(public * com.xxl.job.core.handler.IJobHandler+.*(..)) || @annotation(com.xxl.job.core.handler.annotation.XxlJob)")
        public Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
            return MonitorLogAop.processAround(pjp, LogParser.Default.buildInstance("$.code==" + ReturnT.SUCCESS_CODE), LogPoint.xxljob);
        }
    }
}
