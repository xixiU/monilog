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
     * @param p
     */
    void logDigest(MoniLogParams p);

    /**
     * 打印详情日志
     * @param p
     */
    void logDetail(MoniLogParams p);

    /**
     * 获取logger实例，默认会取相关业务类的logger实例
     * @param p
     * @return
     */
    default Logger getLogger(MoniLogParams p) {
        Class<?> serviceCls = p == null ? MoniLogUtil.class : p.getServiceCls();
        if (serviceCls == null) {
            serviceCls = MoniLogUtil.class;
        }
        return LoggerFactory.getLogger(serviceCls);
    }
}
