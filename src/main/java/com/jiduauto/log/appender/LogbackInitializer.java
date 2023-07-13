package com.jiduauto.log.appender;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.jiduauto.log.MonitorLogProperties;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author yp
 * @date 2023/07/12
 */
class LogbackInitializer {
    @Resource
    private MonitorLogProperties properties;

    @Resource
    private LogAppenderProvider logAppenderProvider;

    public void init() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        List<Appender<ILoggingEvent>> appenders = logAppenderProvider.buildAppenders(ctx, properties);
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
}
