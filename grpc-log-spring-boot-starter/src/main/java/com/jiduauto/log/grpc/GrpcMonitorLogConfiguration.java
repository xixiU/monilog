package com.jiduauto.log.grpc;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * @author dianming.cao
 * @date 2022/8/16
 */
@Configuration
@ConditionalOnProperty(prefix = "monitor.log.grpc", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('grpc')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('grpc'))")
@ConditionalOnClass(name = {"io.grpc.stub.AbstractStub", "io.grpc.stub.ServerCalls"})
@Slf4j
class GrpcMonitorLogConfiguration {


    @GrpcGlobalServerInterceptor
    @Order(-100)
    @ConditionalOnProperty(prefix = "monitor.log.grpc.server", name = "enable", havingValue = "true", matchIfMissing = true)
    GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
        return new GrpcLogPrintServerInterceptor();
    }

    @GrpcGlobalClientInterceptor
    @Order(-101)
    @ConditionalOnClass(name = "io.grpc.ClientInterceptor")
    @ConditionalOnProperty(prefix = "monitor.log.grpc.client", name = "enable", havingValue = "true", matchIfMissing = true)
    GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
        return new GrpcLogPrintClientInterceptor();
    }
}
