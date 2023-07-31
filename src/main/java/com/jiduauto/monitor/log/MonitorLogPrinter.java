package com.jiduauto.monitor.log;


/**
 * @author yp
 * @date 2023/07/12
 */
public interface MonitorLogPrinter {
    /**
     * 打印摘要日志
     * @param p
     */
    void logDigest(MonitorLogParams p);

    /**
     * 打印详情日志
     * @param p
     */
    void logDetail(MonitorLogParams p);
}
