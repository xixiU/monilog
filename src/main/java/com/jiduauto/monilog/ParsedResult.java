package com.jiduauto.monilog;

import lombok.Getter;
import lombok.Setter;

/**
 * @author yp
 */
@Getter
@Setter
class ParsedResult {
    private boolean success;
    private String msgCode;
    private String msgInfo;

    public ParsedResult(boolean success, String msgCode, String msgInfo) {
        this.success = success;
        this.msgCode = msgCode;
        this.msgInfo = msgInfo;
    }
}
