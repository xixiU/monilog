package com.jiduauto.log.core.parse;

import lombok.Getter;
import lombok.Setter;

/**
 * @author yp
 */
@Getter
@Setter
public class ParsedResult {
    private boolean success;
    private String msgCode;
    private String msgInfo;
    public ParsedResult(boolean success) {
        this.success = success;
    }

    public ParsedResult(boolean success, String msgCode, String msgInfo) {
        this.success = success;
        this.msgCode = msgCode;
        this.msgInfo = msgInfo;
    }
}
