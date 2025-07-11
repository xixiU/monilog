package com.example.monilog;

import lombok.Getter;

/**
 * @author yp
 * @date 2023/07/12
 */
@Getter
class ErrorInfo {
    private String errorCode;
    private String errorMsg;
    public static ErrorInfo of(String errorCode, String errorMsg) {
        ErrorInfo e = new ErrorInfo();
        e.errorCode = errorCode;
        e.errorMsg = errorMsg;
        return e;
    }
}
