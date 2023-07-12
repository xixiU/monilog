package com.jiduauto.log;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author yp
 * @date 2023/07/12
 */
class LogbackInitializer extends InstantiationAwareBeanPostProcessorAdapter implements BeanFactoryAware {
    @Resource
    private MonitorLogProperties properties;

    private LogAppenderProvider logAppenderProvider;

    private BeanFactory beanFactory;

    public void init() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        LogAppenderProvider provider = getProvider();
        List<Appender<ILoggingEvent>> appenders = provider.buildAppenders(ctx, properties);
        if (CollectionUtils.isEmpty(appenders)) {
            return;
        }
        ctx.reset();

        Logger rootLogger = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        rootLogger.setAdditive(true);
        for (Appender<ILoggingEvent> appender : appenders) {
            rootLogger.addAppender(appender);
        }
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        try {
            Object bean = beanFactory.getBean(LogAppenderProvider.class);
        } catch (Exception ignore) {
        }
    }

    private LogAppenderProvider getProvider() {
        if (this.logAppenderProvider != null) {
            return this.logAppenderProvider;
        }
        try {
            LogAppenderProvider provider = beanFactory.getBean(LogAppenderProvider.class);
            this.logAppenderProvider = provider;
            return provider;
        } catch (Exception e) {
            return new LogAppenderProvider() {
                @Override
                public List<Appender<ILoggingEvent>> buildAppenders(LoggerContext ctx, MonitorLogProperties properties) {
                    return LogAppenderProvider.super.buildAppenders(ctx, properties);
                }
            };
        }
    }
}
