package com.jiduauto.log.enums;

import lombok.Getter;

/**
 * @author yp
 */
@Getter
public enum LogPoint {
    /**
     * 切点类型
     */
    RPC_ENTRY("RPC服务入口"),
    WEB_ENTRY("Web服务入口"),
    TASK_ENTRY("任务入口"),
    MSG_ENTRY("消息入口"),
    REMOTE_CLIENT("下游依赖", false),
    DAL_CLIENT("数据存储依赖", false),
    MSG_PRODUCER("消息发送出口", false),
    UNKNOWN_ENTRY("未知入口");
    private final String desc;
    private final boolean entrance;

    LogPoint(String desc, boolean entrance) {
        this.desc = desc;
        this.entrance = entrance;
    }

    LogPoint(String desc) {
        this(desc, true);
    }
}
