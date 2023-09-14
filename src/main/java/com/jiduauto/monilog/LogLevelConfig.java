package com.jiduauto.monilog;

import lombok.Getter;
import lombok.Setter;

/**
 * @author yp
 * @date 2023/09/15
 */
@Getter
@Setter
class LogLevelConfig {
    /**
     * 当接口响应结果被判定为false时，monilog输出的日志级别
     */
    private LogLevel falseResult = LogLevel.ERROR;
    /**
     * 当出现慢调用时，monilog输出的日志级别
     */
    private LogLevel longRt = LogLevel.ERROR;
    /**
     * 当出现超大值时，monilog输出的日志级别
     */
    private LogLevel largeSize = LogLevel.ERROR;
}
