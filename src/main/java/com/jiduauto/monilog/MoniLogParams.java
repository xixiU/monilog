package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * @author yp
 * @date 2023/07/12
 */
@Getter
@Setter
public class MoniLogParams implements Serializable {
    private static final long serialVersionUID = 1L;
    private Class<?> serviceCls;
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

    private String[] tags;
    private boolean hasUserTag;

    public String getMsgCode() {
        boolean blank = StringUtils.isBlank(msgCode);
        if (!blank) {
            if (!success && ErrorEnum.SUCCESS.name().equals(msgCode)) {
                blank = true;
            }
            if (success && ErrorEnum.FAILED.name().equals(msgCode)) {
                blank = true;
            }
        }
        return blank ? success ? ErrorEnum.SUCCESS.name() : ErrorEnum.FAILED.name() : msgCode;
    }

    public String getMsgInfo() {
        boolean blank = StringUtils.isBlank(msgInfo);
        if (!blank) {
            if (!success && ErrorEnum.SUCCESS.getMsg().equals(msgInfo)) {
                blank = true;
            }
            if (success && ErrorEnum.FAILED.getMsg().equals(msgInfo)) {
                blank = true;
            }
        }
        return blank ? (success ? ErrorEnum.SUCCESS.getMsg() : ErrorEnum.FAILED.getMsg()) : msgInfo;
    }

    @Override
    public String toString() {
        String success = this.success ? "true" : "false";
        String rt = this.cost + "ms";
        String input = JSON.toJSONString(getInput());
        String output = JSON.toJSONString(getOutput());

        String tagStr = tags == null || tags.length == 0 ? "" : "|" + Arrays.toString(tags);
        String pattern = "[%s]-%s.%s|%s|%s|%s|%s%s input:%s, output:%s";
        List<Object> argList = Lists.newArrayList(logPoint, service, action, success, rt, msgCode, msgInfo, tagStr, input, output);
        if (exception != null) {
            pattern += ", ex:%s";
            argList.add(ExceptionUtil.getErrorMsg(exception));
            return String.format(pattern, argList.toArray(new Object[0]));
        }
        return String.format(pattern, argList.toArray(new Object[0]));
    }
}
