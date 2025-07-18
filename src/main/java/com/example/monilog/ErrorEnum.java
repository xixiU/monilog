package com.example.monilog;

import lombok.Getter;

/**
 * @author yp
 * @date 2023/07/12
 */
@Getter
enum ErrorEnum {
    PARAM_ERROR("参数错误"),
    FAILED("失败"),
    SERVICE_TIMEOUT("服务超时"),
    SYSTEM_ERROR("系统异常"),
    UNKNOWN_HOST("服务解析异常"),
    NULL_RESULT("结果为null"),
    EMPTY_RESULT("结果为空"),
    SUCCESS("成功");

    private final String msg;

    ErrorEnum(String msg) {
        this.msg = msg;
    }
}
