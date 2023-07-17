package com.jiduauto.log.weblogspringbootstarter.model;


import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.beans.Transient;

/**
 * 公共返回类
 * 使用@Accessors(chain = true).setXX()返回对象，推荐使用链式调用new Response().setCode().setMsg();
 * @author udon 2021-09-01
 */
@Data
@Accessors(chain = true)
public class Response {
    /**
     * 返回状态码。 {@link StatusCode} 中定义了最基本的状态码。
     */
    protected Integer code;
    protected String msg;
    protected String showMsg = "";

    /**
     * setter
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T setCode(Integer code) {
        this.code = code;
        return (T) this;
    }

    /**
     * setter
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T setMsg(String msg) {
        this.msg = msg;
        return (T) this;
    }

    /**
     * setter
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T setShowMsg(String showMsg) {
        this.showMsg = showMsg;
        return (T) this;
    }

    /**
     * 快速二分法标记，是否成功或失败。
     *
     * @param status 是否成功
     * @param <T>    Response子类
     * @return 结果对象
     */
    public <T extends Response> T markStatus(boolean status) {
        return markCode(status ? StatusCode.OK : StatusCode.FAIL);
    }

    /**
     * 通过StatusCode状态码标记状态
     *
     * @param code 状态码
     * @param <T>  Response子类
     * @return 结果对象
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T markCode(StatusCode code) {
        this.code = code.getCode();
        if (this.msg == null) {
            this.msg = code.getMsg();
        }
        if ((!this.code.equals(StatusCode.OK.getCode())) && StringUtils.isEmpty(this.showMsg)) {
            this.showMsg = code.getShowMsg();
        }
        return (T) this;
    }

    /**
     * 标记成功
     *
     * @param <T> Response子类
     * @return 结果对象
     */
    public <T extends Response> T ok() {
        return markCode(StatusCode.OK);
    }

    /**
     * 标记失败
     *
     * @param <T> Response子类
     * @return 结果对象
     */
    public <T extends Response> T fail() {
        return markCode(StatusCode.FAIL);
    }

    /**
     * 标记失败
     *
     * @param msg 失败消息
     * @param <T> Response子类
     * @return 结果对象
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T fail(String msg) {
        this.code = StatusCode.FAIL.getCode();
        this.msg = msg;
        this.showMsg = msg;
        return (T) this;
    }


    /**
     * 标记失败
     *
     * @param msg 失败消息
     * @param showMsg 失败提示消息
     * @param <T> Response子类
     * @return 结果对象
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T fail(String msg, String showMsg) {
        this.code = StatusCode.FAIL.getCode();
        this.msg = msg;
        this.showMsg = showMsg;
        return (T) this;
    }

    /**
     * 是否成功
     * 不序列化
     *
     * @return 是否成功
     */
    @Transient
    public boolean isOk() {
        return StatusCode.OK.getCode().equals(code);
    }

    /**
     * 是否不成功
     * 不序列化
     *
     * @return 是否不成功
     */
    @Transient
    public boolean isNotOk() {
        return !isOk();
    }

    /**
     * 使用公共状态码返回信息
     * @param resultCode 结果信息
     * @param <T> Response子类
     * @return 结果对象
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T useEnumCode(ResultCode resultCode) {
        this.code = resultCode.getCode();
        this.msg = resultCode.getDesc();
        if (!this.code.equals(StatusCode.OK.getCode())) {
            this.showMsg = resultCode.getShowMsg();
        }
        return (T) this;
    }

    /**
     * 使用公共状态码返回信息
     * @param resultCode 结果信息
     * @param msg 描述信息
     * @param <T> Response子类
     * @return 结果对象
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T useEnumCode(ResultCode resultCode, String msg) {
        this.code = resultCode.getCode();
        this.msg = StringUtils.isBlank(msg) ? resultCode.getDesc() : msg;
        if (!this.code.equals(StatusCode.OK.getCode())) {
            this.showMsg = StringUtils.isBlank(msg) ? resultCode.getShowMsg() : msg;
        }
        return (T) this;
    }


    /**
     * 使用公共状态码返回信息
     * @param resultCode 结果信息
     * @param msg 描述信息
     * @param showMsg 提示信息
     * @param <T> Response子类
     * @return 结果对象
     */
    @SuppressWarnings("unchecked")
    public <T extends Response> T useEnumCode(ResultCode resultCode, String msg, String showMsg) {
        this.code = resultCode.getCode();
        this.msg = StringUtils.isBlank(msg) ? resultCode.getDesc() : msg;
        if (!this.code.equals(StatusCode.OK.getCode())) {
            this.showMsg = StringUtils.isBlank(showMsg) ? resultCode.getShowMsg() : showMsg;
        }
        return (T) this;
    }

}
