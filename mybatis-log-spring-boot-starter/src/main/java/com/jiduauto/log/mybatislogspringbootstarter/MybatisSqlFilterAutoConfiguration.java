package com.jiduauto.log.mybatislogspringbootstarter;

import com.jiduauto.log.mybatislogspringbootstarter.filter.MybatisSqlFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "monitor.log.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
@Configuration
public class MybatisSqlFilterAutoConfiguration {
    @Bean
    public MybatisSqlFilter mybatisSqlFilter() {
        return new MybatisSqlFilter();
    }
}
