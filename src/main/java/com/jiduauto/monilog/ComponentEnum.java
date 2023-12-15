package com.jiduauto.monilog;

import lombok.Getter;

import java.util.Set;

/**
 * 组件枚举
 *
 * @author rongjie.yuan
 * @date 2023/9/27 13:34
 */
@Getter
enum ComponentEnum {
    //组件
    web,
    feign,
    xxljob,
    httpclient,
    grpc,
    grpc_client,
    grpc_server,
    rocketmq,
    rocketmq_consumer,
    rocketmq_producer,
    mybatis,
    redis;

    boolean isEnable() {
        MoniLogProperties properties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        if (properties == null || !properties.isEnable()) {
            return false;
        }
        boolean componentEnable = false;
        switch (this) {
            case web:
                componentEnable = properties.getWeb() != null && properties.getWeb().isEnable();
                break;
            case feign:
                componentEnable = properties.getFeign() != null && properties.getFeign().isEnable();
                break;
            case xxljob:
                componentEnable = properties.getXxljob() != null && properties.getXxljob().isEnable();
                break;
            case httpclient:
                componentEnable = properties.getHttpclient() != null && properties.getHttpclient().isEnable();
                break;
            case grpc:
                componentEnable = properties.getGrpc() != null && properties.getGrpc().isEnable();
                break;
            case grpc_client:
                componentEnable = properties.getGrpc() != null && properties.getGrpc().isEnable() && properties.getGrpc().isClientEnable();
                break;
            case grpc_server:
                componentEnable = properties.getGrpc() != null && properties.getGrpc().isEnable() && properties.getGrpc().isServerEnable();
                break;
            case rocketmq:
                componentEnable = properties.getRocketmq() != null && properties.getRocketmq().isEnable();
                break;
            case rocketmq_consumer:
                componentEnable = properties.getRocketmq() != null && properties.getRocketmq().isEnable() && properties.getRocketmq().isConsumerEnable();
                break;
            case rocketmq_producer:
                componentEnable = properties.getRocketmq() != null && properties.getRocketmq().isEnable() && properties.getRocketmq().isProducerEnable();
                break;
            case mybatis:
                componentEnable = properties.getMybatis() != null && properties.getMybatis().isEnable();
                break;
            case redis:
                componentEnable = properties.getRedis() != null && properties.getRedis().isEnable();
                break;
            default:
                break;
        }
        if (!componentEnable) {
            return false;
        }

        Set<ComponentEnum> componentExcludes = properties.getComponentExcludes();
        boolean excludeThis = componentExcludes != null && componentExcludes.contains(this);
        if (!excludeThis) {
            //未排除，则enable
            return true;
        }
        Set<ComponentEnum> componentIncludes = properties.getComponentIncludes();
        //即include 又exclude时，以include为准
        return componentIncludes != null && componentIncludes.contains(this);
    }
}