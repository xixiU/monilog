package com.jiduauto.monitor.log;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author ：xiaoxu.bao
 * @date ：2022/8/18 19:41
 */
@Getter
@AllArgsConstructor
enum MonitorType {
    /**
     * 计数
     */
    RECORD(1, "_record", "计数"),

    /**
     * 累加
     */
    CUMULATION(11, "_cumulation", "累加"),

    /**
     * 耗时
     */
    TIMER(21, "_timer", "耗时");

    private final int code;

    private final String mark;

    private final String desc;

}
