package com.jiduauto.log.grpc;

import com.jiduauto.log.grpc.filter.GrpcLogPrintClientInterceptor;
import com.jiduauto.log.grpc.filter.GrpcLogPrintServerInterceptor;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ServerCalls;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * @author dianming.cao
 * @date 2022/8/16
 */
@Configuration
@ConditionalOnProperty(prefix = "monitor.log.grpc", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass({AbstractStub.class, ServerCalls.class})
@Slf4j
public class GrpcMonitorLogConfiguration {


    @GrpcGlobalServerInterceptor
    @Order(-100)
    @ConditionalOnProperty(prefix = "monitor.log.grpc.server", name = "enable", havingValue = "true", matchIfMissing = true)
    GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
        log.info("grpcLogPrintServerInterceptor init...");
        return new GrpcLogPrintServerInterceptor();
    }

    @GrpcGlobalClientInterceptor
    @Order(-101)
    @ConditionalOnProperty(prefix = "monitor.log.grpc.client", name = "enable", havingValue = "true", matchIfMissing = true)
    GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
        log.info("grpcLogPrintClientInterceptor init...");
        return new GrpcLogPrintClientInterceptor();
    }


}
