package com.jiduauto.log;

import lombok.Getter;
import lombok.Setter;

/**
 * @author yp
 * @date 2023/07/12
 */
@Getter
@Setter
public class MonitorLogParams {
    private LogPoint logPoint;
    private String service;
    private String action;
    private boolean success;
    private String msgCode;
    private String msgInfo;
    private long cost;
    private Throwable exception;
    private Object[] input;
    private Object output;
}
