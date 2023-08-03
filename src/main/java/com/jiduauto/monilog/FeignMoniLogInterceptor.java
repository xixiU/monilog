package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import feign.Client;
import feign.Request;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author yp
 * @date 2023/07/24
 */
@Slf4j
class FeignMoniLogInterceptor {
//    @Override
//    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//        if (bean instanceof Client ) {
//            return getProxyBean(bean);
//        }
//        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
//    }
//
//    @Override
//    public int getOrder() {
//        return Ordered.HIGHEST_PRECEDENCE;
//    }

    public static Client getProxyBean(Object bean) {
        return (Client) ProxyUtils.getProxy(bean, new FeignExecuteInterceptor());
    }

    private static class FeignExecuteInterceptor implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            long start = System.currentTimeMillis();
            Object result = null;
            Throwable ex = null;
            try {
                result = invocation.proceed();
            } catch (Throwable t) {
                ex = t;
            }
            long cost = System.currentTimeMillis() - start;
            Method m = invocation.getMethod();
            String methodName = m.getName();
            int parameterCount = m.getParameterCount();
            Class<?>[] parameterTypes = m.getParameterTypes();
            boolean isTargetMethod = methodName.equals("execute") && parameterCount == 2 && parameterTypes[0] == Request.class && parameterTypes[1] == Request.Options.class;
            if (!isTargetMethod) {
                if (ex == null) {
                    return result;
                } else {
                    throw ex;
                }
            }
            MoniLogProperties properties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
            Response ret = (Response) result;
            ret = doFeignInvocationRecord(m, (Request) (invocation.getArguments()[0]), ret, cost, ex, properties == null ? null : properties.getFeign());
            if (ex == null) {
                return ret;
            } else {
                throw ex;
            }
        }

        private Response doFeignInvocationRecord(Method m, Request request, Response response, long cost, Throwable ex, MoniLogProperties.FeignProperties feignProperties) {
            String requestURI = request.url();
            Set<String> urlBlackList = feignProperties == null ? new HashSet<>() : feignProperties.getUrlBlackList();
            if (CollectionUtils.isEmpty(urlBlackList)) {
                urlBlackList = new HashSet<>();
            }
            if (StringUtil.checkPathMatch(urlBlackList, requestURI)) {
                return response;
            }

            MoniLogParams mlp = new MoniLogParams();
            mlp.setServiceCls(m.getDeclaringClass());
            mlp.setService(m.getDeclaringClass().getSimpleName());
            mlp.setAction(m.getName());

            StackTraceElement st = ThreadUtil.getNextClassFromStack(m.getDeclaringClass(), "feign","org.springframework");
            if (st != null) {
                String className = st.getClassName();
                try {
                    mlp.setServiceCls(Class.forName(className));
                } catch (Exception ignore) {}
                mlp.setService(mlp.getServiceCls().getSimpleName());
                mlp.setAction(st.getMethodName());
            }

            mlp.setTags(new String[]{"method", request.httpMethod().toString(), "url", requestURI});

            mlp.setCost(cost);
            mlp.setException(ex);
            mlp.setSuccess(ex == null && response.status() < HttpStatus.BAD_REQUEST.value());
            mlp.setLogPoint(LogPoint.feign_client);
            mlp.setInput(new Object[]{formatRequestInfo(request)});
            mlp.setMsgCode(ErrorEnum.SUCCESS.name());
            mlp.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            if (ex != null) {
                mlp.setSuccess(false);
                ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
                if (errorInfo != null) {
                    mlp.setMsgCode(errorInfo.getErrorCode());
                    mlp.setMsgInfo(errorInfo.getErrorMsg());
                }
                return response;
            }
            //包装响应
            Charset charset = request.charset();
            if (charset == null) {
                charset = StandardCharsets.UTF_8;
            }
            Response ret = null;
            try {
                BufferingFeignClientResponse bufferedResp = new BufferingFeignClientResponse(response);
                mlp.setSuccess(mlp.isSuccess() && response.status() < HttpStatus.BAD_REQUEST.value());
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
                MoniLogUtil.log("doFeignInvocationRecord error: {}", e.getMessage());
                ret = response;
            } finally {
                MoniLogUtil.log(mlp);
            }
            return ret;
        }
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
