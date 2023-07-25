package com.jiduauto.log.grpc;

import com.jiduauto.log.grpc.filter.GrpcLogPrintClientInterceptor;
import com.jiduauto.log.grpc.filter.GrpcLogPrintServerInterceptor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * @author dianming.cao
 * @date 2022/8/16
 */
@Configuration
@ConditionalOnClass(GrpcClient.class)
@ConditionalOnProperty(prefix = "monitor.log.grpc", name = "enable", havingValue = "true", matchIfMissing = true)
public class GrpcMonitorLogConfiguration {


    @ConditionalOnMissingBean
    @GrpcGlobalServerInterceptor
    @Order(-100)
    @ConditionalOnProperty(prefix = "monitor.log.grpc.server", name = "enable", havingValue = "true", matchIfMissing = true)
    GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
        return new GrpcLogPrintServerInterceptor();
    }

    @ConditionalOnMissingBean
    @GrpcGlobalClientInterceptor
    @Order(-101)
    @ConditionalOnProperty(prefix = "monitor.log.grpc.client", name = "enable", havingValue = "true", matchIfMissing = true)
    GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
        return new GrpcLogPrintClientInterceptor();
    }


}
