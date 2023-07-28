package com.jiduauto.monitor.log;

/**
 * @author : udon 2022-04-21
 */
class Constants {
    /**
     * 业务监控tag值为空的处理
     */
    public static final String NO_VALUE_CODE = "00";

    /**
     * 下划线
     */
    public static final String UNDERLINE = "_";

    /**
     * 点
     */
    public static final String DOT = ".";


    public static final String RESULT = "result";

    public static final String SUCCESS = "success";

    public static final String MSG_CODE = "msgCode";

    /**
     * 异常的类
     */
    public static final String EXCEPTION = "exception";

    /**
     * 错误
     */
    public static final String EXCEPTION_MSG = "exceptionMsg";


    /**
     * 统一的埋点前缀
     */
    public static final String BUSINESS_NAME_PREFIX = "business_monitor";


    public static final String ERROR = "error";

    /**
     * 应用名称
     */
    public static final String APPLICATION = "application";

    public static final String LOG_POINT = "logPoint";

    /**
     * 服务名
     */
    public static final String SERVICE_NAME = "service";

    /**
     * 方法名
     */
    public static final String ACTION_NAME = "action";

    /**
     * 耗时
     */
    public static final String COST = "cost";

    /**
     * 环境
     */
    public static final String ENV = "env";

    /**
     * 框架异常信息前缀
     */
    public static final String SYSTEM_ERROR_PREFIX = "__monitor_log_warn__";

}
