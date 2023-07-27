
package com.jiduauto.log.xxljob;

import com.jiduauto.log.core.CoreMonitorLogConfiguration;
import com.jiduauto.log.core.LogParser;
import com.jiduauto.log.core.aop.MonitorLogAop;
import com.jiduauto.log.core.enums.LogPoint;
import com.xxl.job.core.handler.IJobHandler;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({IJobHandler.class, CoreMonitorLogConfiguration.class})
@ConditionalOnProperty(prefix = "monitor.log.xxljob", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('xxljob')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('xxljob'))")
class XxlJobMonitorLogConfiguration {
    @Bean
    public XxlJobLogMonitorExecuteInterceptor xxlJobExecuteInterceptor() {
        return new XxlJobLogMonitorExecuteInterceptor();
    }

    @Aspect
    @Slf4j
    static class XxlJobLogMonitorExecuteInterceptor {
        @Value("${monitor.log.xxljob.bool.expr.default:$.code==200}")
        private String defaultBoolExpr;

        @Around("execution(* com.xxl.job.core.handler.IJobHandler+.execute(..))")
        public Object interceptXxlJob(ProceedingJoinPoint pjp) throws Throwable {
            return MonitorLogAop.processAround(pjp, LogParser.Default.buildInstance(defaultBoolExpr), LogPoint.xxljob);
        }
    }
}
