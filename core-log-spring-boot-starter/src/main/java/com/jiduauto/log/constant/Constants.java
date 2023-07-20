package com.jiduauto.log.constant;

/**
 * @author : udon 2022-04-21
 */
public class Constants {

    public static final String TRACE_ID = "trace_id";

    /**
     * 业务监控tag值为空的处理
     */
    public static final String NO_VALUE_CODE = "00";

    public static final String EXCEPTION  ="exception";

    public static final String COMMA_BRACKETS = ",}";

    public static final String BRACKETS = "}";

    /**
     * 点
     */
    public static final String DOT = ".";


    public static final String RESULT = "result";

    public static final String SUCCESS = "success";

    public static final String ERROR = "error";

    /**
     * 应用名称
     */
    public static final String APPLICATION = "application";

    public static final String LOG_POINT = "logPoint";

    /**
     * 环境
     */
    public static final String ENV = "env";

    // mq相关常量

    /**
     * rocketmq header name
     */
    public static final String TX_ID_HEADER_NAME = "rocketmq_TRANSACTION_ID";
    public static final String MSG_ID_HEADER_NAME = "id";

}
