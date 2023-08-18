package com.jiduauto.monilog;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

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
     * 日志前缀
     *
     * @return
     */
    default String getLogPrefix() {
        return SpringUtils.LOG_PREFIX;
    }

    /**
     * 打印超时日志
     */
    default void logLongRt(MoniLogParams p) {
        if (p == null) {
            return;
        }
        Logger logger = getLogger(p);
        String logPoint = p.getLogPoint().name();
        String service = p.getService();
        String action = p.getAction();
        String success = p.isSuccess() ? "true" : "false";
        String code = p.getMsgCode();
        String msg = p.getMsgInfo();
        String[] tags = p.getTags();
        String tagStr = tags == null || tags.length == 0 ? "" : "|" + Arrays.toString(tags);
        String rt = p.getCost() + "ms";
        logger.error("{}rt_too_long[{}]-{}.{}|{}|{}|{}|{}{}", getLogPrefix(), logPoint, service, action, success, code, msg, rt, tagStr);
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
