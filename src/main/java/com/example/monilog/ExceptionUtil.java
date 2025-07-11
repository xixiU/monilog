package com.example.monilog;


import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * @author yepei
 */
class ExceptionUtil {
    public static ErrorInfo parseException(Throwable ex) {
        if (ex == null) {
            return null;
        }
        if (ex instanceof IllegalArgumentException) {
            return ErrorInfo.of(ErrorEnum.PARAM_ERROR.name(), getErrorMsg(ex));
        }
        if (ex instanceof UnknownHostException) {
            return ErrorInfo.of(ErrorEnum.UNKNOWN_HOST.name(), ReflectUtil.getSimpleClassName(ex.getClass()) + ":" + ex.getMessage());
        }
        boolean isTimeout = ExceptionUtil.isTimeout(ex);
        ErrorEnum ee = isTimeout ? ErrorEnum.SERVICE_TIMEOUT : ErrorEnum.SYSTEM_ERROR;
        String msg = ExceptionUtil.getErrorMsg(ex, ee.getMsg());
        return ErrorInfo.of(ee.name(), msg);
    }

    public static boolean isTimeout(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof InvocationTargetException) {
            return isTimeout(((InvocationTargetException) e).getTargetException());
        }
        return isTimeoutException(e) || isTimeoutException(e.getCause());
    }

    public static String getErrorMsg(Throwable e) {
        return getErrorMsg(e, null);
    }

    public static String getErrorMsg(Throwable e, String defaultMsg) {
        if (e == null) {
            return defaultMsg;
        }
        if (e instanceof InvocationTargetException) {
            return getErrorMsg(((InvocationTargetException) e).getTargetException(), defaultMsg);
        }
        String msg = e.getMessage();
        if (StringUtils.isBlank(msg) && e.getCause() != null) {
            msg = e.getCause().getMessage();
        }
        if (StringUtils.isBlank(msg)) {
            return StringUtils.isBlank(defaultMsg) ? ReflectUtil.getSimpleClassName(e.getClass()):
                    defaultMsg + "(" + ReflectUtil.getSimpleClassName(e.getClass()) + ")";
        }
        return msg;
    }

    private static boolean isTimeoutException(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof java.net.SocketTimeoutException
                || e instanceof SQLTimeoutException
                || e instanceof TimeoutException) {
            return true;
        }
        String msg = ReflectUtil.getSimpleClassName(e.getClass()) + ":" + e.getLocalizedMessage();
        return StringUtils.containsIgnoreCase(msg, "timeout") || StringUtils.containsIgnoreCase(msg, "timed out");
    }

    /**
     * 因为ReflectUtil将受检异常包装成了运行时异常，所以这里尝试找到真实异常
     */
    public static Throwable getRealException(Throwable e) {
        if (e instanceof InvocationTargetException) {
            return ((InvocationTargetException) e).getTargetException();
        }
        if (e instanceof ReflectiveOperationException) {
            return e;
        }
        //找到原异常
        Throwable cause = e.getCause();
        if (cause == null) {
            return e;
        }
        if (cause instanceof InvocationTargetException) {
            return ((InvocationTargetException) cause).getTargetException();
        }
        if (cause instanceof ReflectiveOperationException) {
            return cause;
        }
        if (cause.getCause() instanceof ReflectiveOperationException) {
            return cause.getCause();
        }
        return e instanceof RuntimeException ? cause : e;
    }
}
