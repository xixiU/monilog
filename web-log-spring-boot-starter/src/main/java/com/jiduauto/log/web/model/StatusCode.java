package com.jiduauto.log.web.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * @author udon 2021-09-01
 */
@Getter
@AllArgsConstructor
public enum StatusCode {
    /**
     * 请求成功
     */
    OK(0, "请求成功", "请求成功"),
    /**
     * 请求失败
     */
    FAIL(20000, "请求失败", "请求失败"),
    /**
     * 请求熔断
     */
    BREAKER(20002, "请求熔断", "请求熔断"),
    /**
     *
     */
    NOT_EXIST(21003, "数据不存在", "数据不存在"),
    ;
    private final Integer code;
    private final String msg;
    private final String showMsg;

    public static StatusCode fromCode(Integer value) {
        return Arrays.stream(StatusCode.values()).filter(it -> it.getCode().equals(value)).findFirst().orElse(null);
    }

    /**
     * 通过code获取名称，没有就返回默认
     *
     * @param code        代码
     * @param defaultName 名称
     * @return 响应
     */
    public static String getNameWithDefault(Integer code, String defaultName) {
        StatusCode StatusCode = fromCode(code);
        return StatusCode == null ? defaultName : StatusCode.getMsg();
    }
}

