package com.jiduauto.log.grpclogspringbootstarter;

import com.jiduauto.log.grpclogspringbootstarter.filter.GrpcLogPrintClientInterceptor;
import com.jiduauto.log.grpclogspringbootstarter.filter.GrpcLogPrintServerInterceptor;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ServerCalls;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * @author dianming.cao
 * @date 2022/8/16
 */
@Configuration
@ConditionalOnClass({AbstractStub.class, ServerCalls.class})
public class GrpcMonitorLogConfiguration {


    @GrpcGlobalServerInterceptor
    @Order(-100)
    GrpcLogPrintServerInterceptor grpcLogPrintServerInterceptor() {
        return new GrpcLogPrintServerInterceptor();
    }

    @GrpcGlobalClientInterceptor
    @Order(-101)
    GrpcLogPrintClientInterceptor grpcLogPrintClientInterceptor() {
        return new GrpcLogPrintClientInterceptor();
    }


}
