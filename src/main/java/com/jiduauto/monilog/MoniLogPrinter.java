package com.jiduauto.monilog;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author yp
 * @date 2023/07/12
 */
public interface MoniLogPrinter {
    /**
     * 打印摘要日志
     */
    void logDigest(MoniLogParams p);

    /**
     * 打印详情日志
     */
    void logDetail(MoniLogParams p);

    /**
     * 打印慢日志
     */
    void logLongRt(MoniLogParams p);

    /**
     * 打印大值监控日志
     *
     * @param logParams 执行上下文
     * @param key       关联key
     */
    void logLargeSize(MoniLogParams logParams, String key);

    /**
     * 日志前缀
     */
    default String getLogPrefix() {
        return SpringUtils.LOG_PREFIX;
    }

    /**
     * 获取logger实例，默认会取相关业务类的logger实例
     */
    default Logger getLogger(MoniLogParams p) {
        Class<?> serviceCls = p == null ? MoniLogUtil.class : p.getServiceCls();
        if (serviceCls == null) {
            serviceCls = MoniLogUtil.class;
        }
        return LoggerFactory.getLogger(serviceCls);
    }
}
