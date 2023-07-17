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

    public static final String COMMA_BRACKETS = ",}";

    public static final String BRACKETS = "}";

    /**
     * 点
     */
    public static final String DOT = ".";

    /**
     * sql耗时过长
     */
    public static final Long SQL_TAKING_TOO_LONG = 2000L;

    public static final String RESULT = "result";

    public static final String SUCCESS = "success";

    public static final String ERROR = "error";



    public static final String SQL_QUERY_START = "select";

    /**
     * rocketmq header name
     */
    public static final String TX_ID_HEADER_NAME = "rocketmq_TRANSACTION_ID";
    public static final String MSG_ID_HEADER_NAME = "id";

}
