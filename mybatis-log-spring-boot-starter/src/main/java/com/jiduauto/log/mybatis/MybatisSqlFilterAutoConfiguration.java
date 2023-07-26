package com.jiduauto.log.mybatis;

import com.jiduauto.log.mybatis.filter.MybatisMonitorSqlFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "monitor.log.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.endpoints.include:*}'.equals('*') or '${monitor.log.endpoints.include}'.contains('mybatis')) and !('${monitor.log.endpoints.exclude:}'.equals('*') or '${monitor.log.endpoints.exclude:}'.contains('mybatis'))")
@Configuration
public class MybatisSqlFilterAutoConfiguration {
    @Bean
    public MybatisMonitorSqlFilter mybatisMonitorSqlFilter() {
        return new MybatisMonitorSqlFilter();
    }
}
