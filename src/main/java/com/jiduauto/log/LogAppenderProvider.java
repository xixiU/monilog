package com.jiduauto.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yp
 * @date 2023/07/12
 */
public interface LogAppenderProvider {
    default List<Appender<ILoggingEvent>> buildAppenders(LoggerContext ctx, MonitorLogProperties properties) {
        List<Appender<ILoggingEvent>> list = new ArrayList<>();
        list.add(getErrorAppender(ctx, properties));
        list.add(getDigestAppender(ctx, properties));
        list.add(getConsoleAppender(ctx, properties));
        list.add(getAppAppender(ctx, properties));
        list.add(getDetailAppender(ctx, properties));
        return list;
    }

    static ConsoleAppender<ILoggingEvent> getConsoleAppender(LoggerContext ctx, MonitorLogProperties properties) {
        return AppenderBuilder.buildAndStartConsoleAppender("console", ctx, properties.getPattern());
    }

    static RollingFileAppender<ILoggingEvent> getDigestAppender(LoggerContext ctx, MonitorLogProperties properties) {
        return AppenderBuilder.buildAndStartRollingFileAppender(
                "digest",
                ctx,
                null,
                properties.getLogDir(),
                "digest.log",
                properties.getPattern(),
                properties.getMaxLogHistory(),
                properties.getMaxLogSize()
        );
    }

    static RollingFileAppender<ILoggingEvent> getDetailAppender(LoggerContext ctx, MonitorLogProperties properties) {
        return AppenderBuilder.buildAndStartRollingFileAppender(
                "detail",
                ctx,
                null,
                properties.getLogDir(),
                "detail.log",
                properties.getPattern(),
                properties.getMaxLogHistory(),
                properties.getMaxLogSize()
        );
    }

    static RollingFileAppender<ILoggingEvent> getAppAppender(LoggerContext ctx, MonitorLogProperties properties) {
        return AppenderBuilder.buildAndStartRollingFileAppender(
                "app",
                ctx,
                null,
                properties.getLogDir(),
                "app.log",
                properties.getPattern(),
                properties.getMaxLogHistory(),
                properties.getMaxLogSize()
        );
    }

    static RollingFileAppender<ILoggingEvent> getErrorAppender(LoggerContext ctx, MonitorLogProperties properties) {
        return AppenderBuilder.buildAndStartRollingFileAppender(
                "error",
                ctx,
                AppenderBuilder.getLevelFilter(ctx, Level.ERROR),
                properties.getLogDir(),
                "error.log",
                properties.getPattern(),
                properties.getMaxLogHistory(),
                properties.getMaxLogSize()
        );
    }
}
