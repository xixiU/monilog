package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import feign.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author yp
 * @date 2023/07/24
 */
@Slf4j
public final class FeignMoniLogInterceptor {
    /**
     * 为Client.execute()注册拦截器, 此处是通过Javassist将处理后的结果直接传入
     * 注：该方法不可修改，包括可见级别，否则将导致HttpClient拦截失效
     */
    public static Response doRecord(Request request, Response response, long cost, Throwable ex) {
        try {
            Method execute = Client.Default.class.getDeclaredMethod("execute", Request.class, Request.Options.class);
            return doFeignInvocationRecord(execute, request, response, cost, ex);
        } catch (Throwable e) {
            MoniLogUtil.innerDebug("FeignMoniLogInterceptor.doRecord error", e);
        }
        return response;
    }

    /**
     * 需要注意request与response流的消耗
     */
    private static Response doFeignInvocationRecord(Method m, Request request, Response response, long cost, Throwable ex) {
        MoniLogProperties properties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        MoniLogProperties.FeignProperties feignProperties = properties == null ? null : properties.getFeign();
        if (feignProperties == null || !properties.isComponentEnable(ComponentEnum.feign, feignProperties.isEnable())) {
            return response;
        }
        String requestUri = request.url();
        Set<String> urlBlackList = feignProperties.getUrlBlackList();
        if (CollectionUtils.isEmpty(urlBlackList)) {
            urlBlackList = new HashSet<>();
        }
        if (StringUtil.checkPathMatch(urlBlackList, requestUri)) {
            return response;
        }

        MoniLogParams mlp = new MoniLogParams();
        mlp.setServiceCls(m.getDeclaringClass());
        mlp.setService(ReflectUtil.getSimpleClassName(m.getDeclaringClass()));
        mlp.setAction(m.getName());

        StackTraceElement st = ThreadUtil.getNextClassFromStack(Client.class, "feign", "com.netflix", "rx");
        if (st != null) {
            String className = st.getClassName();
            try {
                mlp.setServiceCls(Class.forName(className));
            } catch (Exception ignore) {
            }
            mlp.setService(ReflectUtil.getSimpleClassName(mlp.getServiceCls()));
            mlp.setAction(st.getMethodName());
        }

        mlp.setTags(new String[]{"method", getMethod(request), "url", HttpRequestData.extractPath(requestUri)});

        mlp.setCost(cost);
        mlp.setException(ex);
        mlp.setSuccess(ex == null && response.status() < HttpStatus.BAD_REQUEST.value());
        mlp.setLogPoint(LogPoint.feign_client);
        mlp.setInput(new Object[]{formatRequestInfo(request)});
        mlp.setMsgCode(ErrorEnum.SUCCESS.name());
        mlp.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
        if (ex != null) {
            handleException(ex, mlp);
            MoniLogUtil.log(mlp);
            return response;

        }
        Response ret;
        BufferingFeignClientResponse bufferedResp = null;
        try {
            bufferedResp = new BufferingFeignClientResponse(response);
            mlp.setSuccess(mlp.isSuccess() && response.status() < HttpStatus.BAD_REQUEST.value());
            if (!mlp.isSuccess()) {
                mlp.setMsgCode(String.valueOf(bufferedResp.status()));
                mlp.setMsgInfo(ErrorEnum.FAILED.getMsg());
            }
            String resultStr = null;
            if (bufferedResp.isDownstream()) {
                mlp.setOutput("Binary data");
            } else {
                resultStr = bufferedResp.getBodyAsString();
                mlp.setOutput(resultStr);
            }
            if (resultStr != null && bufferedResp.isJson()) {
                Object json = JSON.parse(resultStr);
                if (json != null) {
                    mlp.setOutput(json);
                    LogParser cl = ReflectUtil.getAnnotation(LogParser.class, mlp.getServiceCls(), m);
                    //尝试更精确的提取业务失败信息
                    String specifiedBoolExpr = StringUtils.trimToNull(feignProperties.getDefaultBoolExpr());
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
            // 再次新建一个流
            ret = bufferedResp.getResponse();
            Util.ensureClosed(bufferedResp);
        } catch (Exception e) {
            // 在执行解析的过程中可能会出现连接中断，这种情况需要把异常抛出去
            if (e instanceof FeignException) {
                handleException(e, mlp);
                return response;
            } //其他异常可能是monilog的bug导致的
            MoniLogUtil.innerDebug("doFeignInvocationRecord error", e);
            ret = bufferedResp == null ? response : bufferedResp.getResponse();
        } finally {
            MoniLogUtil.log(mlp);
        }
        return ret;
    }

    /**
     * 当有异常时设置MoniLogParams并返回
     */
    private static void handleException(Throwable ex, MoniLogParams mlp) {
        mlp.setSuccess(false);
        ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
        if (errorInfo != null) {
            mlp.setMsgCode(errorInfo.getErrorCode());
            mlp.setMsgInfo(errorInfo.getErrorMsg());
        }
    }


    /**
     * com.netflix.feign:feign-core 8.18.0 中没有调用的method与openfeign不同
     *
     * @see Request
     * openfeign定义如下
     * <blockquote><pre>
     * public final class feign.Request {
     * private final HttpMethod httpMethod;
     *
     * private final String url;
     *
     * private final Map<String, Collection<String>> headers;
     *
     * private final Body body;
     *
     * private final RequestTemplate requestTemplate;
     *
     * public enum HttpMethod {
     * GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE, PATCH;
     * }
     * }
     * </pre></blockquote><p>
     * <p>
     * 而com.netflix.feign.Request定义如下：
     * <blockquote><pre>
     *    public final class com.netflix.feign.Request {
     *   private final String method;
     *
     *   private final String url;
     *
     *   private final Map<String, Collection<String>> headers;
     *
     *   private final byte[] body;
     *
     *   private final Charset charset;
     *
     *   public static Request create(String method, String url, Map<String, Collection<String>> headers, byte[] body, Charset charset) {
     *     return new Request(method, url, headers, body, charset);
     *   }
     * }
     */
    private static String getMethod(Request request) {
        // openfeign
        boolean hasHttpMethod = ReflectUtil.hasProperty(request.getClass(), "httpMethod");
        if (hasHttpMethod) {
            Object propValue = ReflectUtil.getPropValue(request, "httpMethod");
            return propValue != null ? propValue.toString() : null;
        }
        // netflix
        return ReflectUtil.getPropValue(request, "method", "unknown");
    }

    private static JSONObject formatRequestInfo(Request request) {
        String bodyParams = getBodyParams(request);
        Map<String, Collection<String>> headers = request.headers();
        Map<String, Collection<String>> queries = getQuery(request);
        return HttpRequestData.of2(request.url(), bodyParams, queries, headers).toJSON();
    }

    private static Map<String, Collection<String>> getQuery(Request request) {
        //com.netflix.feign根据feign.RequestTemplate.request原来可以看到query参数也会拼接到url中
        Map<String, Collection<String>> queryMap = StringUtil.getQueryMap(request.url());
        if (queryMap == null) {
            queryMap = new HashMap<>();
        }
        // openfeign加一个兜底逻辑可以从requestTemplate参数取值
        boolean hasRequestTemplate = ReflectUtil.hasProperty(request.getClass(), "requestTemplate");
        if (hasRequestTemplate) {
            Map<String, Collection<String>> queries = request.requestTemplate().queries();
            queryMap.putAll(queries);
        }
        return queryMap.isEmpty() ? null : queryMap;
    }

    // com.netflix.feign:feign-core 8.18.0 中没有request.length()方法
    private static int length(byte[] body) {
        return body != null ? body.length : 0;
    }

    private static String getBodyParams(Request request) {
        byte[] body = request.body();
        if (length(body) == 0) {
            return null;
        }
        //com.netflix.feign:feign-core 8.18.0 中没有request.isBinary()方法
        return StringUtil.encodeByteArray(body, request.charset(), "Binary data");
    }

    private static class BufferingFeignClientResponse implements Closeable {
        private final Response originResponse;
        private Response response;
        private final byte[] buffer;

        BufferingFeignClientResponse(Response response) throws IOException {
            this.originResponse = response;
            this.buffer = response.body() == null ? null : Util.toByteArray(response.body().asInputStream());
            this.response = response.toBuilder().body(this.buffer).build();
        }

        Response getResponse() {
            return response;
        }

        int status() {
            return this.response.status();
        }

        private Map<String, Collection<String>> headers() {
            return this.response.headers();
        }

        String getBodyAsString() {
            Charset charset = response.request() == null ? null : response.request().charset();
            if (charset == null) {
                charset = StandardCharsets.UTF_8;
            }
            String bodyString = StringUtil.encodeByteArray(buffer, charset, "Binary data");
            //复原response
            this.response = response.toBuilder().body(buffer).build();
            return bodyString;
        }

        boolean isDownstream() {
            String header = HttpUtil.getFirstHeader(headers(), HttpHeaders.CONTENT_DISPOSITION);
            return StringUtils.containsIgnoreCase(header, "attachment") || StringUtils.containsIgnoreCase(header, "filename");
        }

        boolean isJson() {
            if (isDownstream()) {
                return false;
            }
            String header = HttpUtil.getFirstHeader(headers(), HttpHeaders.CONTENT_TYPE);
            return StringUtils.containsIgnoreCase(header, "application/json");
        }

        @Override
        public void close() {
            this.originResponse.close();
        }
    }
}
