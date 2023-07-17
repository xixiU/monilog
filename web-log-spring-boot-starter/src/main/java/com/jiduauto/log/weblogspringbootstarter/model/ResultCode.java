package com.jiduauto.log.weblogspringbootstarter.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author udon 2021-09-26
 */
@AllArgsConstructor
@Getter
public enum ResultCode {
    /**
     * 结果状态码
     */
    SUCCESS(0, "请求处理成功", "请求处理成功"),
    FAIL(10000, "请求处理失败", "请求处理失败"),
    CANNOT_TASK_UP(10011, "不可上架", "不可上架"),
    CANNOT_TASK_OFF(10012, "不可下架", "不可下架"),
    TOKEN_ERROR(10047, "请登录后再次操作", "请登录后再次操作"),
    PARAMETER_ERROR(10050, "用户参数错误", "用户参数错误"),
    INSERT_FAIL(20001, "新增失败", "新增失败"),
    UPDATE_FAIL(20002, "更新失败", "更新失败"),
    DUPLICATE_VALUE(20003, "数据已存在", "数据已存在"),
    RESOURCE_NOT_FOUND(20004, "资源未找到", "资源未找到"),
    DB_ERROR(20005, "数据库异常", "数据库异常"),
    UNKNOWN(21000, "请求处理结果未知", "请求处理结果未知"),
    BREAKER(21002, "请求熔断", "请求熔断"),
    NOT_EXIST(21003, "数据不存在", "数据不存在"),
    DATA_NEED_REFRESH(21004, "数据已失效，请刷新后重试", "数据已失效，请刷新后重试"),
    RESUBMIT_ORDER_FAIL(21005, "请勿重复提交失败订单", "请勿重复提交失败订单"),
    ;

    private int code;

    private String desc;

    private String showMsg;

}
