package com.jiduauto.log.feign;

import feign.Client;
import feign.Feign;
import feign.Request;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.util.StreamUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.util.Collection;
import java.util.Map;

/**
 * @author yp
 * @date 2023/07/24
 */
@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "monitor.log.feign", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(Feign.class)
@Slf4j
public class FeignMonitorLogConfiguration {

    @Bean
    public Client getClient() {
        return new MonitorLogClient(null, null);
    }


    static class MonitorLogClient extends Client.Default {
        public MonitorLogClient(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
            super(sslContextFactory, hostnameVerifier);
        }

        public MonitorLogClient(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, boolean disableRequestBuffering) {
            super(sslContextFactory, hostnameVerifier, disableRequestBuffering);
        }

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            log.info("begin to invoke...");
            long start = System.currentTimeMillis();
            BufferingFeignClientResponse response = null;
            try {
                response = new BufferingFeignClientResponse(super.execute(request, options));
            } catch (Exception e) {
                throw e;
            } finally {

            }
            long cost = System.currentTimeMillis() - start;
            log.info("after invoked, cost:{}ms", cost);
            return null;
        }
    }

}
