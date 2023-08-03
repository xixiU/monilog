package com.jiduauto.monilog;


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
}
