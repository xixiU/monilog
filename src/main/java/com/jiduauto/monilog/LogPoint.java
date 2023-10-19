package com.jiduauto.monilog;

import lombok.Getter;

/**
 * @author yp
 */
@Getter
public enum LogPoint {
    /**
     * 切点类型
     */
    http_server(true),
    feign_server(true),
    grpc_server(true),
    rocketmq_consumer(true),
    xxljob(true),

    http_client,
    feign_client,
    grpc_client,
    rocketmq_producer,
    mybatis,
    redis,
    okHttpClient,
    /**
     * 用户自定义业务维度
     */
    user_define,
    unknown;

    private final boolean entrance;

    LogPoint(boolean entrance) {
        this.entrance = entrance;
    }

    LogPoint() {
        this(false);
    }
}
