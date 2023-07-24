package com.jiduauto.log.feign;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jiduauto.log.core.ErrorInfo;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.parse.ParsedResult;
import com.jiduauto.log.core.parse.ResultParseStrategy;
import com.jiduauto.log.core.util.ExceptionUtil;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.jiduauto.log.core.util.ResultParseUtil;
import feign.Client;
import feign.Feign;
import feign.Request;
import feign.Response;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
    @ConditionalOnBean(Client.class)
    @Primary
    public MonitorLogClient getClient(Client c) {
        MonitorLogClient client = new MonitorLogClient(null, null);
        client.setC(c);
        return client;
    }


    @Setter
    static class MonitorLogClient extends Client.Default {
        private Client c;


        public MonitorLogClient(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
            super(sslContextFactory, hostnameVerifier);
        }

        public MonitorLogClient(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, boolean disableRequestBuffering) {
            super(sslContextFactory, hostnameVerifier, disableRequestBuffering);
        }

        @SneakyThrows
        @Override
        public Response execute(Request request, Request.Options options) {
            log.info("begin to invoke...");
            long start = System.currentTimeMillis();
            Response originResponse = null;
            Throwable ex = null;
            long cost = 0;
            try {
                //原始调用
                originResponse = c.execute(request, options);
            } catch (Throwable e) {
                ex = e;
            } finally {
                cost = System.currentTimeMillis() - start;
            }

            //TODO 这里需要拿到feign的目标接口信息，并尝试提取其上的LogParser注解
            MonitorLogParams mlp = new MonitorLogParams();
            mlp.setServiceCls(null);
            mlp.setService("");
            mlp.setAction("");
            mlp.setTags(new String[]{""});


            mlp.setCost(cost);
            mlp.setException(ex);
            mlp.setSuccess(ex == null);
            mlp.setLogPoint(LogPoint.REMOTE_CLIENT);
            mlp.setInput(new Object[]{request.toString()});
            mlp.setMsgCode(ErrorEnum.SUCCESS.name());
            mlp.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            if (ex != null) {
                ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
                if (errorInfo != null) {
                    mlp.setMsgCode(errorInfo.getErrorCode());
                    mlp.setMsgInfo(errorInfo.getErrorMsg());
                }
                throw ex;
            }
            //包装响应
            Charset charset = request.charset();
            if (charset == null) {
                charset = StandardCharsets.UTF_8;
            }
            Response ret;
            try {
                BufferingFeignClientResponse response = new BufferingFeignClientResponse(originResponse);
                mlp.setSuccess(mlp.isSuccess() && response.status() == HttpStatus.OK.value());
                if (!mlp.isSuccess()) {
                    mlp.setMsgCode(String.valueOf(response.status()));
                    mlp.setMsgInfo(ErrorEnum.FAILED.getMsg());
                }
                String resultStr = null;
                if (response.isDownstream()) {
                    mlp.setOutput("Binary data");
                } else {
                    resultStr = IOUtils.toString(originResponse.body().asReader(charset)); //读掉原始response中的数据
                    mlp.setOutput(resultStr);
                }
                if (resultStr != null && response.isJson()) {
                    Object json = JSON.parse(resultStr);
                    if (json instanceof JSONObject) {
                        //尝试更精确的提取业务失败信息
                        //TODO 这里如何能基于配置或接口注解中的判定表达式更准确判断结果呢？ 例如: $.code==0
                        ParsedResult parsedResult = ResultParseUtil.parseResult(json, ResultParseStrategy.IfSuccess, null);
                        mlp.setSuccess(parsedResult.isSuccess());
                        mlp.setMsgCode(parsedResult.getMsgCode());
                        mlp.setMsgInfo(parsedResult.getMsgInfo());
                    }
                }
                if (resultStr != null) {
                    //重写将数据写入原始response中去
                    ret = response.getResponse().toBuilder().body(resultStr, charset).build();
                } else {
                    ret = response.getResponse();
                }
                response.close();
            } catch (Exception e) {
                return originResponse;
            } finally {
                MonitorLogUtil.log(mlp);
            }
            return ret;
        }
    }

}
