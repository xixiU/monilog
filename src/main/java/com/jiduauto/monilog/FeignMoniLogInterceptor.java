package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import feign.Client;
import feign.Request;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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
public final class FeignMoniLogInterceptor {
    /**
     * 为Client.execte()注册拦截器, 此处是通过Javassist将处理后的结果直接传入
     * 注：该方法不可修改，包括可见级别，否则将导致HttpClient拦截失效
     */
    public static Response doFeignInvocation(Request request, Response response, long cost, Throwable ex){
        try{
            Method execute = Client.Default.class.getDeclaredMethod("execute", Request.class, Request.Options.class);
            doFeignInvocationRecord(execute , request, response, cost, ex);
        }catch (Throwable e){
            MoniLogUtil.innerDebug("doFeignInvocation error", e);
        }
        return response;
    }


    /**
     * 需要注意request与response流的消耗
     */
    private static Response doFeignInvocationRecord(Method m, Request request, Response response, long cost, Throwable ex) {
        MoniLogProperties properties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
        MoniLogProperties.FeignProperties feignProperties = properties == null ? null : properties.getFeign();
        if (feignProperties == null || !feignProperties.isEnable()) {
            return response;
        }
        String requestUri = request.url();
        Set<String> urlBlackList = feignProperties == null ? new HashSet<>() : feignProperties.getUrlBlackList();
        if (CollectionUtils.isEmpty(urlBlackList)) {
            urlBlackList = new HashSet<>();
        }
        if (StringUtil.checkPathMatch(urlBlackList, requestUri)) {
            return response;
        }

        MoniLogParams mlp = new MoniLogParams();
        mlp.setServiceCls(m.getDeclaringClass());
        mlp.setService(m.getDeclaringClass().getSimpleName());
        mlp.setAction(m.getName());

        StackTraceElement st = ThreadUtil.getNextClassFromStack(Client.class, "feign", "org.springframework","com.netflix","rx","com.jiduauto.monilog");
        if (st != null) {
            String className = st.getClassName();
            try {
                mlp.setServiceCls(Class.forName(className));
            } catch (Exception ignore) {
            }
            mlp.setService(mlp.getServiceCls().getSimpleName());
            mlp.setAction(st.getMethodName());
        }

        mlp.setTags(new String[]{"method", getMethod(request), "url", requestUri});

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
            MoniLogUtil.log(mlp);
            return response;
        }
        //包装响应
        Charset charset = request.charset();
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        Response ret;
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
                //重写将数据写入原始response的body中
                ret = bufferedResp.getResponse(resultStr, charset);
            } else {
                ret = bufferedResp.getResponse();
            }
            bufferedResp.close();
        } catch (Exception e) {
            MoniLogUtil.innerDebug("doFeignInvocationRecord error", e);
            ret = response;
        } finally {
            MoniLogUtil.log(mlp);
        }
        return ret;
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
        return HttpRequestData.of2(bodyParams, queries, headers).toJSON();
    }

    private static Map<String, Collection<String>> getQuery(Request request) {
        // com.netflix.feign根据feign.RequestTemplate.request原来可以看到query参数也会拼接到url中
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


    // com.netflix.feign:feign-core 8.18.0 中没有request.isBinary()方法
    private static boolean isBinary(byte[] body, Charset charset) {
        return charset == null || body == null;
    }

    // com.netflix.feign:feign-core 8.18.0 中没有request.length()方法
    private static int length(byte[] body) {
        return body != null ? body.length : 0;
    }

    // 注意这里消耗的流
    private static String getBodyParams(Request request) {
        byte[] body = request.body();
        if (isBinary(body, request.charset())) {
            return "Binary data";
        }
        if (length(body) == 0) {
            return null;
        }
        return new String(body, request.charset()).trim();
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

        Response getResponse(String text, Charset charset) {
            try {
                Class<?> cls = Class.forName("feign.Response$ByteArrayBody");
                Method orNull = cls.getDeclaredMethod("orNull", String.class, Charset.class);
                orNull.setAccessible(true);
                Object body = orNull.invoke(null, text, charset);
                // 通过反射获取Response.body,并修改值
                ReflectUtil.setPropValue(this.response, "body", body, true);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("setResponseBody error", e);
            }
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
