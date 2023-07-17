package com.jiduauto.log.weblogspringbootstarter.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.beans.Transient;
import java.util.Optional;

/**
 * 带返回数据的，返回类。 使用@Accessors(chain = true).setXX()返回对象，推荐使用链式调用new DataResponse<>().setData().ok()
 *
 * @author udon 2021-08-19
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DataResponse<T> extends Response {

    private T data;

    public DataResponse<T> setData(T data) {
        this.data = data;
        return this;
    }

    public DataResponse<T> optionalData(Optional<T> data) {
        data.ifPresent(t -> this.data = t);
        return this;
    }

    @Transient
    public boolean isEmpty() {
        return isNotOk() || data == null;
    }

    @Transient
    public boolean isNotEmpty() {
        return !isEmpty();
    }

    public static <T> DataResponse<T> success() {
        return success(null);
    }

    public static <T> DataResponse<T> success(T data) {
        return create(StatusCode.OK.getCode(), "success", "", data);
    }


    public static <T> DataResponse<T> fail(int code, String msg, String showMsg) {
        return create(code, msg, showMsg, null);
    }

    private static <T> DataResponse<T> create(int code, String msg, String showMsg, T data) {
        DataResponse<T> dataResponse = new DataResponse<>();
        dataResponse.setCode(code);
        dataResponse.setMsg(msg);
        dataResponse.setShowMsg(showMsg);
        dataResponse.setData(data);
        return dataResponse;
    }
}
