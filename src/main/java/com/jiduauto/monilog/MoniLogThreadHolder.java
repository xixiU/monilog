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


    public static void setTraceId(String traceId) {
        TRACE_ID_THREAD_LOCAL.set(traceId);
    }

    public static String getTraceId() {
        return TRACE_ID_THREAD_LOCAL.get();
    }


    protected static void clear() {
        TRACE_ID_THREAD_LOCAL.remove();
    }
}
