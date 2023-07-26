package com.jiduauto.log.mybatis;

import com.jiduauto.log.core.MonitorLogConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "monitor.log.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('mybatis')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('mybatis'))")
@ConditionalOnClass(MonitorLogConfiguration.class)
@Configuration
class MybatisSqlFilterAutoConfiguration {
    @Bean
    public MybatisInterceptor mybatisMonitorSqlFilter() {
        return new MybatisInterceptor();
    }
}
