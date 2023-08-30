package com.jiduauto.monilog;

import org.apache.http.protocol.HttpContext;

/**
 * @author yp
 * @date 2023/08/30
 */
public final class AsyncClientExceptionHandler {
    /**
     * 该类不可修改，包括可见级别，否则将导致AsyncHttpClient拦截失效
     */
    public static void onFailed(Exception ex, HttpContext context) {

    }
}
