package com.jiduauto.log.appender;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.FileSize;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import ch.qos.logback.core.filter.Filter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author yp
 * @date 2023/07/12
 */
public class AppenderBuilder {
    /**
     * @param logFileName
     * @param pattern
     * @param maxLogHistory
     * @param maxLogSize
     * @return
     */
    public static RollingFileAppender<ILoggingEvent> buildAndStartRollingFileAppender(
            String name,
            LoggerContext ctx,
            Filter<ILoggingEvent> filter,
            String logDir,
            String logFileName,
            String pattern,
            int maxLogHistory,
            String maxLogSize
    ) {
        if (!StringUtils.endsWith(logFileName, ".log")) {
            throw new IllegalArgumentException("日志文件名应当以.log结尾");
        }
        String fileName = Paths.get(logDir, logFileName).toString();
        // 创建并配置FileAppender
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setName(name);
        appender.setPrudent(false);
        appender.setAppend(true);
        appender.setFile(fileName);
        appender.setContext(ctx);
        PatternLayoutEncoder encoder = getEncoder(ctx, pattern);
        SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = getRollingPolicy(ctx, fileName, appender, maxLogHistory, maxLogSize);
        appender.setEncoder(encoder);
        if (filter != null) {
            appender.addFilter(filter);
            filter.start();
        }

        encoder.start();
        rollingPolicy.start();
        appender.start();
        return appender;
    }


    public static ConsoleAppender<ILoggingEvent> buildAndStartConsoleAppender(String name, LoggerContext ctx, String pattern) {
        // 创建并配置FileAppender
        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setName(name);
        appender.setContext(ctx);
        PatternLayoutEncoder encoder = getEncoder(ctx, pattern);
        appender.setEncoder(encoder);
        encoder.start();
        appender.start();
        return appender;
    }

    /**
     *
     */
    public static AsyncAppender buildAndStartAsyncAppender(
            String name,
            LoggerContext ctx,
            int discardingThreshold,
            int queueSize,
            boolean neverBlock,
            boolean includeCallerData,
            List<Appender<ILoggingEvent>> appenderRefs
    ) {
        if (CollectionUtils.isEmpty(appenderRefs)) {
            return null;
        }
        AsyncAppender appender = new AsyncAppender();
        appender.setName(name);
        appender.setDiscardingThreshold(discardingThreshold);
        appender.setContext(ctx);
        appender.setIncludeCallerData(includeCallerData);
        appender.setNeverBlock(neverBlock);
        appender.setQueueSize(queueSize);
        for (Appender<ILoggingEvent> a : appenderRefs) {
            appender.addAppender(a);
        }
        appender.start();
        return appender;
    }

    public static PatternLayoutEncoder getEncoder(LoggerContext ctx, String pattern) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern(pattern);
        encoder.setCharset(StandardCharsets.UTF_8);
        return encoder;
    }

    public static SizeAndTimeBasedRollingPolicy<ILoggingEvent> getRollingPolicy(LoggerContext ctx, String fileName, FileAppender<?> appender, int maxLogHistory, String maxLogSize) {
        SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
        String file = StringUtils.removeEnd(fileName, ".log");
        rollingPolicy.setFileNamePattern(file + ".%d{yyyy-MM-dd}.%i.log.gz"); // 设置日志文件滚动策略
        rollingPolicy.setMaxHistory(maxLogHistory);
        rollingPolicy.setMaxFileSize(FileSize.valueOf(maxLogSize));
        rollingPolicy.setContext(ctx);
        rollingPolicy.setParent(appender);
        return rollingPolicy;
    }

    public static LevelFilter getLevelFilter(LoggerContext ctx, Level level) {
        LevelFilter f = new LevelFilter();
        f.setLevel(level);
        f.setOnMatch(FilterReply.ACCEPT);
        f.setOnMismatch(FilterReply.DENY);
        f.setContext(ctx);
        return f;
    }
}
