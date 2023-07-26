package com.jiduauto.log.feign;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jiduauto.log.core.ErrorInfo;
import com.jiduauto.log.core.LogParser;
import com.jiduauto.log.core.MonitorLogConfiguration;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.parse.ParsedResult;
import com.jiduauto.log.core.parse.ResultParseStrategy;
import com.jiduauto.log.core.util.*;
import feign.*;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yp
 * @date 2023/07/24
 */
@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "monitor.log.feign", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('feign')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('feign'))")
@ConditionalOnClass({Feign.class, MonitorLogConfiguration.class})
@Slf4j
class FeignMonitorLogConfiguration {
    @Value("${monitor.log.feign.bool.expr.default:$.code==0,$.code==200}")
    private String defaultBoolExpr;

    @Bean
    public FeignClientEnhanceProcessor feignClientEnhanceProcessor() {
        return new FeignClientEnhanceProcessor(defaultBoolExpr);
    }

    @AllArgsConstructor
    static class FeignClientEnhanceProcessor implements BeanPostProcessor, Ordered {
        private final String defaultBoolExpr;

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof Client && !(bean instanceof EnhancedFeignClient)) {
                return new EnhancedFeignClient((Client) bean, defaultBoolExpr);
            }
            return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
        }

        @Override
        public int getOrder() {
            return Integer.MAX_VALUE;
        }
    }


    static class EnhancedFeignClient implements Client {
        private final Client realClient;
        private final String defaultBoolExpr;

        public EnhancedFeignClient(Client realClient, String defaultBoolExpr) {
            this.realClient = realClient;
            this.defaultBoolExpr = defaultBoolExpr;
        }

        @SneakyThrows
        @Override
        public Response execute(Request request, Request.Options options) {
            long start = System.currentTimeMillis();
            Response originResponse = null;
            Throwable ex = null;
            long cost = 0;
            try {
                //原始调用
                originResponse = realClient.execute(request, options);
            } catch (Throwable e) {
                ex = e;
            } finally {
                cost = System.currentTimeMillis() - start;
            }
            MethodMetadata mm = request.requestTemplate().methodMetadata();
            Method m = mm.method();
            MonitorLogParams mlp = new MonitorLogParams();
            mlp.setServiceCls(m.getDeclaringClass());
            mlp.setService(m.getDeclaringClass().getSimpleName());
            mlp.setAction(m.getName());
            mlp.setTags(new String[]{"method", request.httpMethod().toString(), "url", request.url()});

            mlp.setCost(cost);
            mlp.setException(ex);
            mlp.setSuccess(ex == null);
            mlp.setLogPoint(LogPoint.REMOTE_CLIENT);
            mlp.setInput(new Object[]{formatRequestInfo(request)});
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
                    resultStr = response.body(); //读掉原始response中的数据
                    mlp.setOutput(resultStr);
                }
                if (resultStr != null && response.isJson()) {
                    Object json = JSON.parse(resultStr);
                    if (json != null) {
                        mlp.setOutput(json);
                        LogParser cl = ReflectUtil.getAnnotation(LogParser.class, mlp.getServiceCls(), m);
                        //尝试更精确的提取业务失败信息
                        ResultParseStrategy rps = cl == null ? null : cl.resultParseStrategy();//默认使用IfSuccess策略
                        String boolExpr = cl == null ? StringUtils.trimToNull(defaultBoolExpr) : cl.boolExpr();
                        String codeExpr = cl == null ? null : cl.errorCodeExpr();
                        String msgExpr = cl == null ? null : cl.errorMsgExpr();
                        ParsedResult parsedResult = ResultParseUtil.parseResult(json, rps, null, boolExpr, codeExpr, msgExpr);
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

        private static JSONObject formatRequestInfo(Request request) {
            String bodyParams = request.isBinary() ? "Binary data" : request.length() == 0 ? null : new String(request.body(), request.charset()).trim();
            Map<String, Collection<String>> queries = request.requestTemplate().queries();
            Map<String, Collection<String>> headers = request.headers();
            JSONObject obj = new JSONObject();
            if (StringUtils.isNotBlank(bodyParams)) {
                JSON json = StringUtil.tryConvert2Json(bodyParams);
                obj.put("body", json != null ? json : bodyParams);
            }
            if (MapUtils.isNotEmpty(queries)) {
                obj.put("query", StringUtil.encodeQueryString(queries));
            }
            if (MapUtils.isNotEmpty(headers)) {
                Map<String, String> headerMap = new HashMap<>();
                for (Map.Entry<String, Collection<String>> me : headers.entrySet()) {
                    headerMap.put(me.getKey(), String.join(",", me.getValue()));
                }
                obj.put("headers", headerMap);
            }
            return obj;
        }
    }


    static class BufferingFeignClientResponse implements Closeable {
        private Response response;
        private byte[] body;

        BufferingFeignClientResponse(Response response) {
            this.response = response;
        }

        Response getResponse() {
            return this.response;
        }

        int status() {
            return this.response.status();
        }

        private Map<String, Collection<String>> headers() {
            return this.response.headers();
        }


        boolean isDownstream() {
            String header = getFirstHeader(HttpHeaders.CONTENT_DISPOSITION);
            return StringUtils.containsIgnoreCase(header, "attachment")
                    || StringUtils.containsIgnoreCase(header, "filename");
        }

        boolean isJson() {
            if (isDownstream()) {
                return false;
            }
            String header = getFirstHeader(HttpHeaders.CONTENT_TYPE);
            return StringUtils.containsIgnoreCase(header, "application/json");
        }

        String getFirstHeader(String name) {
            if (headers() == null || StringUtils.isBlank(name)) {
                return null;
            }
            for (Map.Entry<String, Collection<String>> me : headers().entrySet()) {
                if (me.getKey().equalsIgnoreCase(name)) {
                    Collection<String> headers = me.getValue();
                    if (headers == null || headers.isEmpty()) {
                        return null;
                    }
                    return headers.iterator().next();
                }
            }
            return null;
        }

        String body() throws IOException {
            StringBuilder sb = new StringBuilder();
            try (InputStreamReader reader = new InputStreamReader(getBody())) {
                char[] tmp = new char[1024];
                int len;
                while ((len = reader.read(tmp, 0, tmp.length)) != -1) {
                    sb.append(new String(tmp, 0, len));
                }
            }
            return sb.toString();
        }

        private InputStream getBody() throws IOException {
            if (this.body == null) {
                this.body = StreamUtils.copyToByteArray(this.response.body().asInputStream());
            }
            return new ByteArrayInputStream(this.body);
        }

        @Override
        public void close() {
            this.response.close();
        }
    }
}
