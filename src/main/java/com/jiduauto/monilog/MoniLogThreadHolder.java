package com.jiduauto.monilog;

/**
 * MoniLog线程工具
 * 
 * @author rongjie.yuan
 * @date 2023/11/7 11:45
 */
public class MoniLogThreadHolder {
    /**
     * 设置traceId
     */
    private static final ThreadLocal<String> TRACE_ID_THREAD_LOCAL = new ThreadLocal<>();

    /**
     *
     * 设置自定义traceId，注意，一般情况下不需要设置自定义traceId，MoniLog会自动从MDC中提取traceId。 除非业务方法内部自己操作(添加并删除)了traceId导致MoniLog拦截器在业务方法前后均不能从MDC中取到有效traceId时，可以由业务方借助此方法将自定义的traceId告知MoniLog
     */
    public static void setTraceId(String traceId) {
        TRACE_ID_THREAD_LOCAL.set(traceId);
    }

    protected static String getTraceId() {
        return TRACE_ID_THREAD_LOCAL.get();
    }

}
