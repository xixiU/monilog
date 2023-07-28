package com.jiduauto.monitor.log.model;

import com.jiduauto.monitor.log.enums.ErrorEnum;
import com.jiduauto.monitor.log.enums.LogPoint;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * @author yp
 * @date 2023/07/12
 */
@Getter
@Setter
public class MonitorLogParams {
    private Class<?> serviceCls;
    private LogPoint logPoint;
    private String service;
    private String action;
    private boolean success;
    private String msgCode;
    private String msgInfo;
    private String[] tags;
    private long cost;
    private Throwable exception;
    private Object[] input;
    private Object output;
    private boolean hasUserTag;

    public String getMsgCode() {
        return StringUtils.isBlank(msgCode) ? (success ? ErrorEnum.SUCCESS.name() : ErrorEnum.FAILED.name()) : msgCode;
    }

    public String getMsgInfo() {
        return StringUtils.isBlank(msgInfo) ? (success ? ErrorEnum.SUCCESS.getMsg() : ErrorEnum.FAILED.getMsg()) : msgInfo;
    }
}
