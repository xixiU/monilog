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
    kafka_consumer(true),
    xxljob(true),
    http_client,
    feign_client,
    grpc_client,
    rocketmq_producer,
    kafka_producer,
    mybatis,
    redis,
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

    boolean exceedLongRtThreshold(MoniLogProperties properties, long cost) {
        if (properties == null || !properties.isMonitorLongRt() || cost <= 0) {
            return false;
        }
        switch (this) {
            case xxljob:
                return exceedLongRtThreshold(properties.getXxljob().getLongRt(), cost);
            case redis:
                return exceedLongRtThreshold(properties.getRedis().getLongRt(), cost);
            case mybatis:
                return exceedLongRtThreshold(properties.getMybatis().getLongRt(), cost);
            case grpc_client:
            case grpc_server:
                return exceedLongRtThreshold(properties.getGrpc().getLongRt(), cost);
            case http_client:
                return exceedLongRtThreshold(properties.getHttpclient().getLongRt(), cost);
            case http_server:
                return exceedLongRtThreshold(properties.getWeb().getLongRt(), cost);
            case feign_client:
            case feign_server:
                return exceedLongRtThreshold(properties.getFeign().getLongRt(), cost);
            case rocketmq_consumer:
            case rocketmq_producer:
                return exceedLongRtThreshold(properties.getRocketmq().getLongRt(), cost);
            case kafka_consumer:
            case kafka_producer:
                return exceedLongRtThreshold(properties.getKafka().getLongRt(), cost);
            case unknown:
            case user_define:
            default:
                return false;
        }
    }

    LogOutputLevel getDetailLogLevel(MoniLogProperties properties) {
        LogOutputLevel detailLogLevel = null;
        switch (this) {
            case http_server:
                detailLogLevel = properties.getWeb().getDetailLogLevel();
                break;
            case http_client:
                detailLogLevel = properties.getHttpclient().getDetailLogLevel();
                break;
            case feign_server:
                detailLogLevel = properties.getFeign().getServerDetailLogLevel();
                break;
            case feign_client:
                detailLogLevel = properties.getFeign().getClientDetailLogLevel();
                break;
            case grpc_client:
                detailLogLevel = properties.getGrpc().getClientDetailLogLevel();
                break;
            case grpc_server:
                detailLogLevel = properties.getGrpc().getServerDetailLogLevel();
                break;
            case rocketmq_consumer:
                detailLogLevel = properties.getRocketmq().getConsumerDetailLogLevel();
                break;
            case rocketmq_producer:
                detailLogLevel = properties.getRocketmq().getProducerDetailLogLevel();
                break;
            case kafka_consumer:
                detailLogLevel = properties.getKafka().getConsumerDetailLogLevel();
                break;
            case kafka_producer:
                detailLogLevel = properties.getKafka().getProducerDetailLogLevel();
                break;
            case mybatis:
                detailLogLevel = properties.getMybatis().getDetailLogLevel();
                break;
            case xxljob:
                detailLogLevel = properties.getXxljob().getDetailLogLevel();
                break;
            case redis:
                detailLogLevel = properties.getRedis().getDetailLogLevel();
                break;
            case unknown:
            default:
                break;
        }
        if (detailLogLevel == null) {
            MoniLogProperties.PrinterProperties printerCfg = properties.getPrinter();
            detailLogLevel = printerCfg.getDetailLogLevel();
            if (detailLogLevel == null) {
                detailLogLevel = LogOutputLevel.onFail;
            }
        }
        return detailLogLevel;
    }

    private static boolean exceedLongRtThreshold(long threshold, long actualCost) {
        if (threshold <= 0) {
            return false;
        }
        return threshold < actualCost;
    }
}
