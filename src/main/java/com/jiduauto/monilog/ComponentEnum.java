package com.jiduauto.monilog;

import lombok.Getter;

/**
 * 组件枚举
 * 
 * @author rongjie.yuan
 * @date 2023/9/27 13:34
 */
@Getter
public enum ComponentEnum {
    web,
    feign,
    xxljob,
    httpClient,
    grpc,
    grpc_client,
    grpc_server,
    rocketmq,
    rocketmq_consumer,
    rocketmq_producer,
    mybatis,
    redis,
}
