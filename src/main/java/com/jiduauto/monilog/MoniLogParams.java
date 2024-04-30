package com.jiduauto.monilog;

import cn.hutool.core.bean.copier.BeanCopier;
import cn.hutool.core.bean.copier.CopyOptions;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yp
 * @date 2023/07/12
 */
@Getter
@Setter
public class MoniLogParams implements Serializable {
    static final String PAYLOAD_FORMATTED_OUTPUT = "FORMATTED_OUTPUT";
    static final String PAYLOAD_FORMATTED_INPUT = "FORMATTED_INPUT";
    static final String REQUEST_TRACE_ID = "REQ_TRACE_ID";
    static final String REQUEST_SPAN_ID = "REQ_SPAN_ID";
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

    /**
     * 存放monilog计算过程中的临时数据
     */
    private transient Map<String, Object> payload = new HashMap<>();
    /**
     * 是否是已过时的数据
     */
    private transient boolean outdated;

    public MoniLogParams() {
        //在某些组件的异步场景下，trace信息并不会正常传递，这里尝试获取请求前的trace信息并暂存下来
        addPayload(REQUEST_TRACE_ID, MoniLogUtil.getTraceId());
        addPayload(REQUEST_SPAN_ID, MoniLogUtil.getSpanId());
    }

    /**
     * copy一份
     */
    MoniLogParams copy() {
        MoniLogParams target = new MoniLogParams();
        CopyOptions options = CopyOptions.create().setTransientSupport(false);
        BeanCopier<MoniLogParams> copier = BeanCopier.create(this, target, options);
        copier.copy();
        return target;
    }

    public MoniLogParams addPayload(String key, Object value) {
        if (key == null || value == null) {
            return this;
        }
        this.payload.put(key, value);
        return this;
    }

    public MoniLogParams removePayload(String key) {
        if (key == null || this.payload == null) {
            return this;
        }
        this.payload.remove(key);
        return this;
    }

    public Object getPayload(String key) {
        return this.payload.get(key);
    }

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
