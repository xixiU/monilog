package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sf.uadetector.UserAgentType;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static com.jiduauto.monilog.StringUtil.checkPathMatch;


@Slf4j
@AllArgsConstructor
class WebMoniLogInterceptor extends OncePerRequestFilter {
    /**
     * 集度JNS请求时header中会带X-JIDU-SERVICENAME
     */
    private static final String JIDU_JNS_HEADER = "X-JIDU-SERVICENAME";
    private static final String USER_AGENT = "User-Agent";
    private final MoniLogProperties moniLogProperties;
    private final static List<HandlerMapping> handlerMappings;

    static {
        Map<String, HandlerMapping> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(SpringUtils.getApplicationContext(), HandlerMapping.class, true, false);
        handlerMappings = new ArrayList<>(matchingBeans.values());
    }
    

    @SneakyThrows
    @Override
    public void doFilterInternal(@NonNull HttpServletRequest httpServletRequest, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws IOException, ServletException {
        MoniLogProperties.WebProperties webProperties = moniLogProperties.getWeb();
        if (webProperties == null ) {
            filterChain.doFilter(httpServletRequest, response);
            return;
        }
        boolean isMultipart;
        HttpServletRequest request;
        try{
            isMultipart = ServletFileUpload.isMultipartContent(httpServletRequest);
            request = isMultipart ? httpServletRequest : new RequestWrapper(httpServletRequest);
        }catch (Exception e){
            MoniLogUtil.innerDebug("check multipart error: {}", e.getMessage());
            filterChain.doFilter(httpServletRequest, response);
            return;
        }

        String responseBodyStr = "";
        MoniLogParams logParams = new MoniLogParams();
        HandlerMethod method = null;
        long startTime = System.currentTimeMillis();

        String requestUri = request.getRequestURI();
        Set<String> urlBlackList = webProperties.getUrlBlackList();
        if (checkPathMatch(urlBlackList, requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }
        Map<String, String> requestHeaderMap = new HashMap<>();
        LogPoint logPoint = LogPoint.unknown;
        boolean webEnable = false;
        boolean feignEnable = false;
        try {
            webEnable = moniLogProperties.isComponentEnable(ComponentEnum.web, moniLogProperties.getWeb().isEnable());
            feignEnable = moniLogProperties.isComponentEnable(ComponentEnum.feign, moniLogProperties.getFeign().isEnable());
            requestHeaderMap = getRequestHeaders(request);
            logPoint = parseLogPoint(requestHeaderMap);
            method = getHandlerMethod(request);
        } catch (Exception e) {
            MoniLogUtil.innerDebug("getHandlerMethod error", e);
        }
        if (method == null || (logPoint == LogPoint.feign_server && !feignEnable) || (logPoint == LogPoint.http_server && !webEnable)) {
            filterChain.doFilter(request, response);
            return;
        }


        try {
            MoniLogTags logTags = ReflectUtil.getAnnotation(MoniLogTags.class, method.getBeanType(), method.getMethod());
            List<String> tagList = StringUtil.getTagList(logTags);
            if (tagList != null && tagList.size() > 1) {
                logParams.setHasUserTag(true);
            }
            logParams.setServiceCls(method.getBeanType());
            logParams.setService(method.getBeanType().getSimpleName());
            logParams.setAction(method.getMethod().getName());
            TagBuilder tagBuilder = TagBuilder.of(tagList).add("url", HttpRequestData.extractPath(requestUri)).add("method", request.getMethod());
            logParams.setTags(tagBuilder.toArray());


            Map<String, Object> requestBodyMap = new HashMap<>();
            logParams.setLogPoint(logPoint);
            JSONObject jsonObject = formatRequestInfo(isMultipart, request, requestHeaderMap);
            Object o = jsonObject.get("body");
            if (o instanceof Map) {
                requestBodyMap.putAll((Map<String, Object>) o);
            }
            logParams.setInput(new Object[]{jsonObject});
            logParams.setMsgCode(ErrorEnum.SUCCESS.name());
            logParams.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            logParams.setSuccess(true);
            dealRequestTags(request, logParams, requestHeaderMap, requestBodyMap);
        } catch (Exception e) {
            MoniLogUtil.innerDebug("dealRequestTags error", e);
        }

        logParams.setSuccess(true);
        Exception bizException = null;
        try {
            ContentCachingResponseWrapper wrapperResponse = new ContentCachingResponseWrapper(response);
            try {
                filterChain.doFilter(request, wrapperResponse);
            } catch (Exception e) {
                // 业务异常
                bizException = e;
                throw bizException;
            }
            responseBodyStr = getResponseBody(wrapperResponse);
            logParams.setOutput(responseBodyStr);
            wrapperResponse.copyBodyToResponse();
            JSON json = StringUtil.tryConvert2Json(responseBodyStr);
            if (json != null) {
                logParams.setOutput(json);
            }
            if (json instanceof JSONObject) {
                LogParser cl = ReflectUtil.getAnnotation(LogParser.class, method.getBeanType(), method.getMethod());
                ResultParseUtil.parseResultAndSet(cl, json, logParams);
            }
        } catch (ClientAbortException e){
            // 解析请求时，客服端断开连接，此类异常直接吞掉
            // 防止大量出现【org.apache.catalina.connector.ClientAbortException: java.io.IOException: Broken pipe/java.io.IOException: Connection reset by peer等】
            log.info("ClientAbortException failed: {}", e.getMessage());
        } catch (Exception e) {
            // 业务异常
            if (e == bizException) {
                logParams.setSuccess(false);
                logParams.setException(e);
                ErrorInfo errorInfo = ExceptionUtil.parseException(e);
                logParams.setMsgCode(errorInfo.getErrorCode());
                logParams.setMsgInfo(errorInfo.getErrorMsg());
                throw bizException;
            } else {
                // 组件异常
                MoniLogUtil.innerDebug( "webMoniLogInterceptor process error", e);
            }
        } finally {
            if (logParams.isSuccess() && StringUtils.isNotBlank(responseBodyStr) && isJson(requestHeaderMap)) {
                logParams.setTags(StringUtil.processUserTag(responseBodyStr, logParams.getTags()));
            }
            logParams.setCost(System.currentTimeMillis() - startTime);
            MoniLogUtil.log(logParams);
        }
    }

    private static Map<String, String> getRequestHeaders(HttpServletRequest request) {
        if (request == null) {
            return new HashMap<>();
        }
        Map<String, String> map = new HashMap<>(32);
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            String value = request.getHeader(key);
            map.put(key, value);
        }
        return map;
    }

    private static Map<String, String> getResponseHeaders(HttpServletResponse response) {
        Map<String, String> map = new HashMap<>(32);
        Collection<String> headerNames = response.getHeaderNames();
        for (String key : headerNames) {
            String value = response.getHeader(key);
            map.put(key, value);
        }
        return map;
    }

    private static boolean isJson(Map<String, String> headerMap) {
        if (MapUtils.isEmpty(headerMap)) {
            return false;
        }

        if (isDownstream(headerMap)) {
            return false;
        }
        String header = getMapValueIgnoreCase(headerMap, HttpHeaders.CONTENT_TYPE);
        return StringUtils.containsIgnoreCase(header, MediaType.APPLICATION_JSON_VALUE);
    }

    private static boolean isDownstream(Map<String, String> headerMap) {
        String header = getMapValueIgnoreCase(headerMap, HttpHeaders.CONTENT_DISPOSITION);
        return StringUtils.containsIgnoreCase(header, "attachment")
                || StringUtils.containsIgnoreCase(header, "filename");
    }


    private static HandlerMethod getHandlerMethod(HttpServletRequest request) {
        for (HandlerMapping mapping : handlerMappings) {
            HandlerExecutionChain handlerExecutionChain;
            try {
                handlerExecutionChain = mapping.getHandler(request);
            } catch (Exception e) {
                continue;
            }
            if (handlerExecutionChain == null) {
                continue;
            }
            if (!(handlerExecutionChain.getHandler() instanceof HandlerMethod)) {
                continue;
            }
            // 返回第一个不为空的HandlerMethod
            return (HandlerMethod) handlerExecutionChain.getHandler();
        }
        return null;
    }

    /**
     * 处理请求tag
     */
    private void dealRequestTags(HttpServletRequest request, MoniLogParams logParams, Map<String, String> requestHeaderMap, Map<String, Object> requestBodyMap) {
        String[] oriTags = logParams.getTags();
        Map<String, String> headersMap = MapUtils.isNotEmpty(requestHeaderMap) ? requestHeaderMap : new HashMap<>();


        for (int i = 0; oriTags != null && i < oriTags.length; i++) {
            if (!oriTags[i].startsWith("{") || !oriTags[i].endsWith("}")) {
                continue;
            }
            String parameterName = oriTags[i].substring(1, oriTags[i].length() - 1);
            // 先从url参数取值
            String resultTagValue = request.getParameter(parameterName);

            if (StringUtils.isNotBlank(resultTagValue)) {
                oriTags[i] = resultTagValue;
                continue;
            }
            // 再从body中取值
            resultTagValue = getMapValueIgnoreCase(requestBodyMap, parameterName);
            if (StringUtils.isNotBlank(resultTagValue)) {
                oriTags[i] = resultTagValue;
                continue;
            }
            // 再从header取值
            resultTagValue = getMapValueIgnoreCase(headersMap, parameterName);
            if (StringUtils.isNotBlank(resultTagValue)) {
                oriTags[i] = resultTagValue;
            }
        }
        logParams.setTags(oriTags);
    }

    private JSONObject formatRequestInfo(boolean isMultipart, HttpServletRequest request, Map<String, String> requestHeaderMap) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        String requestBodyParams = isMultipart ? "Binary data" : ((RequestWrapper) request).getBodyString();
        return HttpRequestData.of1(request.getRequestURI(), requestBodyParams, parameterMap, requestHeaderMap).toJSON();
    }

    private static String getResponseBody(ContentCachingResponseWrapper response) {
        Map<String, String> responseHeaders = getResponseHeaders(response);
        if (isDownstream(responseHeaders)) {
            return "Binary data";
        }
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length <=0) {
                return null;
            }
            String payload;
            // 限制length字节数组大于10w时不解析,太长会需要申请一块连续区间，导致应用gc
            if (buf.length > 10000 * 10) {
                payload = "[Data too long length:" + buf.length + "]";
                return payload;
            }
            try {
                payload = new String(buf, wrapper.getCharacterEncoding());
            } catch (UnsupportedEncodingException e) {
                payload = "[unknown]";
            }
            return payload;
        }
        return null;
    }


    private static LogPoint parseLogPoint(Map<String, String> headerMap) {
        // 为空返回不知道
        if (MapUtils.isEmpty(headerMap)) {
            return LogPoint.unknown;
        }
        String jnsHeader = getMapValueIgnoreCase(headerMap, JIDU_JNS_HEADER);
        if (StringUtils.isNotBlank(jnsHeader)) {
            return LogPoint.feign_server;
        }
        String userAgent = getMapValueIgnoreCase(headerMap, USER_AGENT);
        if (StringUtils.isBlank(userAgent)) {
            return LogPoint.unknown;
        }
        UserAgentType userAgentType = UaUtil.parseUserAgentType(userAgent);
        if (UserAgentType.LIBRARY.equals(userAgentType)) {
            return LogPoint.feign_server;
        }
        return LogPoint.http_server;
    }


    private static String getMapValueIgnoreCase(Map<String, ?> headerMap, String headerKey) {
        if (MapUtils.isEmpty(headerMap) || StringUtils.isBlank(headerKey)) {
            return null;
        }
        Object userAgent = headerMap.get(headerKey);
        if (userAgent != null) {
            return userAgent.toString();
        }
        // 全小写
        userAgent = headerMap.get(headerKey.toLowerCase());
        if (userAgent != null) {
            return userAgent.toString();
        }
        // 全大写
        Object o = headerMap.get(headerKey.toUpperCase());
        return o != null ?  o.toString() : null;
    }
}
