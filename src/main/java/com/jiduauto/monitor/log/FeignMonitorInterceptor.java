package com.jiduauto.monitor.log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import feign.Client;
import feign.MethodMetadata;
import feign.Request;
import feign.Response;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
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
@Slf4j
class FeignMonitorInterceptor implements BeanPostProcessor, PriorityOrdered {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Client) {
            return getProxyBean(bean);
        }
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static Client getProxyBean(Object bean) {
        return (Client) ProxyUtils.getProxy(bean, invocation -> {
            Response ret = (Response) invocation.proceed(); //feign.Response
            Method m = invocation.getMethod();
            String methodName = m.getName();
            int parameterCount = m.getParameterCount();
            Class<?>[] parameterTypes = m.getParameterTypes();
            if (methodName.equals("execute") && parameterCount == 2 && parameterTypes[0] == Request.class && parameterTypes[1] == Request.Options.class) {
                //如果是execute方法，把返回结果进行代理包装：在返回结果前后做一些额外的事情
                MonitorLogProperties properties = SpringUtils.getBeanWithoutException(MonitorLogProperties.class);
                ProxiedResponse pr = new ProxiedResponse(ret);
                //由于Response类是final，无法被cglib生成代理 ，因此这里在创建代理前先进行一下包装
                ProxiedResponse proxyResponse = (ProxiedResponse) ProxyUtils.getProxy(pr, mi -> FeignMonitorInterceptor.doIntercept(mi, properties == null ? null : properties.getFeign()));
                //再返回feign的原生response对象
                return proxyResponse == null ? null : proxyResponse.toFeignResponse();
            }
            return ret;
        });
    }

    @AllArgsConstructor
    private static class ProxiedResponse {
        @Delegate
        private final Response response;

        public Object toFeignResponse() {
            return response;
        }
    }

    private static ProxiedResponse doIntercept(MethodInvocation invocation, MonitorLogProperties.FeignProperties feignProperties) throws Throwable {
        long start = System.currentTimeMillis();
        Object[] args = invocation.getArguments();
        Request request = (Request) args[0];
        ProxiedResponse proxiedResponse = null;
        Response originResponse = null;
        Throwable ex = null;
        long cost;
        try {
            //原始调用
            originResponse = (Response) invocation.proceed();
            proxiedResponse = originResponse == null ? null : new ProxiedResponse(originResponse);
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
        mlp.setSuccess(ex == null && proxiedResponse != null && proxiedResponse.status() < HttpStatus.BAD_REQUEST.value());
        mlp.setLogPoint(LogPoint.feign_client);
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
            BufferingFeignClientResponse bufferedResp = new BufferingFeignClientResponse(originResponse);
            mlp.setSuccess(mlp.isSuccess() && originResponse != null && bufferedResp.status() < HttpStatus.BAD_REQUEST.value());
            if (!mlp.isSuccess()) {
                mlp.setMsgCode(String.valueOf(bufferedResp.status()));
                mlp.setMsgInfo(ErrorEnum.FAILED.getMsg());
            }
            String resultStr = null;
            if (bufferedResp.isDownstream()) {
                mlp.setOutput("Binary data");
            } else {
                resultStr = bufferedResp.body(); //读掉原始response中的数据
                mlp.setOutput(resultStr);
            }
            if (resultStr != null && bufferedResp.isJson()) {
                Object json = JSON.parse(resultStr);
                if (json != null) {
                    mlp.setOutput(json);
                    LogParser cl = ReflectUtil.getAnnotation(LogParser.class, mlp.getServiceCls(), m);
                    //尝试更精确的提取业务失败信息
                    String specifiedBoolExpr = StringUtils.trimToNull(feignProperties == null ? null : feignProperties.getDefaultBoolExpr());
                    ResultParseStrategy rps = cl == null ? null : cl.resultParseStrategy();//默认使用IfSuccess策略
                    String boolExpr = cl == null ? specifiedBoolExpr : cl.boolExpr();
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
                ret = bufferedResp.getResponse().toBuilder().body(resultStr, charset).build();
            } else {
                ret = bufferedResp.getResponse();
            }
            bufferedResp.close();
        } catch (Exception e) {
            return proxiedResponse;
        } finally {
            MonitorLogUtil.log(mlp);
        }
        return new ProxiedResponse(ret);
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

    private static class BufferingFeignClientResponse implements Closeable {
        private final Response response;
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
            return StringUtils.containsIgnoreCase(header, "attachment") || StringUtils.containsIgnoreCase(header, "filename");
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
