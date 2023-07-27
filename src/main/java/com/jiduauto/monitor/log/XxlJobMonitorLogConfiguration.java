
package com.jiduauto.monitor.log;


import com.jiduauto.monitor.log.parse.LogParser;
import com.jiduauto.monitor.log.aop.MonitorLogAop;
import com.jiduauto.monitor.log.enums.LogPoint;
import com.jiduauto.monitor.log.model.MonitorLogProperties;
import com.xxl.job.core.handler.IJobHandler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
@ConditionalOnClass({IJobHandler.class, CoreMonitorLogConfiguration.class})
@ConditionalOnProperty(prefix = "monitor.log.xxljob", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('xxljob')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('xxljob'))")
class XxlJobMonitorLogConfiguration {
    @Resource
    private MonitorLogProperties monitorLogProperties;

    @Bean
    public XxlJobLogMonitorExecuteInterceptor xxlJobExecuteInterceptor() {
        return new XxlJobLogMonitorExecuteInterceptor(monitorLogProperties.getXxljob());
    }

    @Aspect
    @Slf4j
    @AllArgsConstructor
    static class XxlJobLogMonitorExecuteInterceptor {
        private MonitorLogProperties.XxljobProperties xxljobProperties;

        @Around("execution(* com.xxl.job.core.handler.IJobHandler+.execute(..))")
        public Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
            return MonitorLogAop.processAround(pjp, LogParser.Default.buildInstance(xxljobProperties.getBoolExprDefault()), LogPoint.xxljob);
        }
    }
}
