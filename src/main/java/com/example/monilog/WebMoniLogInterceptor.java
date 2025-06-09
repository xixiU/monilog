package com.example.monilog;

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

import static com.example.monilog.StringUtil.checkPathMatch;


@Slf4j
@AllArgsConstructor
class WebMoniLogInterceptor extends OncePerRequestFilter {
    private final static Object initLock = new Object();
    /**
     * 集度JNS请求时header中会带X-JIDU-SERVICENAME
     */
    private static final String JIDU_JNS_HEADER = "X-JIDU-SERVICENAME";
    private static final String USER_AGENT = "User-Agent";
    private final MoniLogProperties moniLogProperties;
    private static List<HandlerMapping> handlerMappings;

    @SneakyThrows
    @Override
    @SuppressWarnings("unchecked")
    public void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse resp, @NonNull FilterChain chain) throws IOException, ServletException {
        RequestInfo reqInfo = checkEnable(req);
        if (reqInfo == null) {
            chain.doFilter(req, resp);
            return;
        }
        boolean isMultipart = reqInfo.isMultipart;
        HttpServletRequest request = reqInfo.wrappedRequest;
        HandlerMethod method = reqInfo.method;
        Map<String, String> requestHeaderMap = reqInfo.requestHeaderMap;
        MoniLogParams logParams = new MoniLogParams();
        try {
            MoniLogTags logTags = ReflectUtil.getAnnotation(MoniLogTags.class, method.getBeanType(), method.getMethod());
            if (logTags != null) {
                logParams.setUserMetricName(logTags.metricName());
            }
            List<String> tagList = StringUtil.getTagList(logTags);
            if (tagList != null && tagList.size() > 1) {
                logParams.setUserTags(tagList.toArray(new String[0]));
            }
            logParams.setServiceCls(method.getBeanType());
            logParams.setService(ReflectUtil.getSimpleClassName(method.getBeanType()));
            logParams.setAction(method.getMethod().getName());
            TagBuilder tagBuilder = TagBuilder.of("url", getUrlWithoutPathParam(request)).add("method", request.getMethod());
            logParams.setTags(tagBuilder.toArray());

            Map<String, Object> requestBodyMap = new HashMap<>(3);
            logParams.setLogPoint(reqInfo.logPoint);
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
        String responseBodyStr = "";
        long startTime = System.currentTimeMillis();
        try {
            ContentCachingResponseWrapper wrapperResponse = new ContentCachingResponseWrapper(resp);
            try {
                chain.doFilter(request, wrapperResponse);
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
        } catch (ClientAbortException e) {
            // 解析请求时，客户端断开连接，异常直接抛出去
            logParams.setSuccess(false);
            logParams.setException(e);
            ErrorInfo errorInfo = ExceptionUtil.parseException(e);
            logParams.setMsgCode(errorInfo.getErrorCode());
            logParams.setMsgInfo(errorInfo.getErrorMsg());
            throw e;
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
                MoniLogUtil.innerDebug("webMoniLogInterceptor process error", e);
            }
        } finally {
            if (logParams.isSuccess() && StringUtils.isNotBlank(responseBodyStr) && isJson(requestHeaderMap)) {
                logParams.setTags(StringUtil.processUserTag(responseBodyStr, logParams.getTags()));
            }
            logParams.setCost(System.currentTimeMillis() - startTime);
            MoniLogUtil.log(logParams);
        }
    }

    private RequestInfo checkEnable(HttpServletRequest req) {
        try {
            boolean webEnable = ComponentEnum.web.isEnable();
            boolean feignEnable = ComponentEnum.feign.isEnable();
            if (!webEnable && !feignEnable) {
                return null;
            }
            Map<String, String> requestHeaderMap = new HashMap<>(32);
            HandlerMethod method = null;
            LogPoint logPoint = LogPoint.unknown;
            try {
                requestHeaderMap = getRequestHeaders(req);
                logPoint = parseLogPoint(requestHeaderMap);
                method = getHandlerMethod(req);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("getHandlerMethod error", e);
            }
            if (method == null) {
                return null;
            }
            String requestUri = req.getRequestURI();
            if (logPoint == LogPoint.http_server) {
                MoniLogProperties.WebProperties webProperties = moniLogProperties.getWeb();
                if (!webEnable || webProperties == null || checkPathMatch(webProperties.getUrlBlackList(), requestUri)) {
                    return null;
                }
            } else if (logPoint == LogPoint.feign_server) {
                MoniLogProperties.FeignProperties feignProperties = moniLogProperties.getFeign();
                if (!feignEnable || feignProperties == null || checkPathMatch(feignProperties.getUrlBlackList(), requestUri)) {
                    return null;
                }
            }
            boolean isMultipart = ServletFileUpload.isMultipartContent(req);
            HttpServletRequest request = isMultipart ? req : new RequestWrapper(req);

            RequestInfo reqInfo = new RequestInfo();
            reqInfo.requestHeaderMap = requestHeaderMap;
            reqInfo.logPoint = logPoint;
            reqInfo.method = method;
            reqInfo.isMultipart = isMultipart;
            reqInfo.wrappedRequest = request;
            return reqInfo;
        } catch (Exception e) {
            MoniLogUtil.innerDebug("check checkEnable error: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("all")
    private static String getUrlWithoutPathParam(HttpServletRequest request) {
        String originUrl = HttpUtil.extractPath(request.getRequestURI());
        try {
            Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (attribute == null || !(attribute instanceof Map)) {
                return originUrl;
            }
            Map<String, String> pathParmas = (Map<String, String>) attribute;
            if (pathParmas == null || pathParmas.isEmpty()) {
                return originUrl;
            }
            for (Map.Entry<String, String> entry : pathParmas.entrySet()) {
                String paramName = entry.getKey();
                String paramValue = entry.getValue();
                if (!originUrl.contains("/" + paramValue)) {
                    continue;
                }
                originUrl = originUrl.replaceAll("/" + paramValue, "{" + paramName + "}");
            }
            return originUrl;
        } catch (Exception e) {
            return originUrl;
        }
    }

    private static Map<String, String> getRequestHeaders(HttpServletRequest request) {
        if (request == null) {
            return new HashMap<>(32);
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

        if (HttpUtil.isDownstream(headerMap)) {
            return false;
        }
        String header = HttpUtil.getMapValueIgnoreCase(headerMap, HttpHeaders.CONTENT_TYPE);
        return StringUtils.containsIgnoreCase(header, MediaType.APPLICATION_JSON_VALUE);
    }


    private static HandlerMethod getHandlerMethod(HttpServletRequest request) {
        for (HandlerMapping mapping : getHandlerMappings()) {
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

    private static List<HandlerMapping> getHandlerMappings() {
        if (handlerMappings != null) {
            return handlerMappings;
        }
        synchronized (initLock) {
            if (handlerMappings != null) {
                return handlerMappings;
            }
            Map<String, HandlerMapping> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(SpringUtils.getApplicationContext(), HandlerMapping.class, true, false);
            handlerMappings = new ArrayList<>(matchingBeans.values());
        }
        return handlerMappings;
    }

    /**
     * 处理请求tag
     */
    private void dealRequestTags(HttpServletRequest request, MoniLogParams logParams, Map<String, String> requestHeaderMap, Map<String, Object> requestBodyMap) {
        String[] oriTags = logParams.getUserTags();
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
            resultTagValue = HttpUtil.getMapValueIgnoreCase(requestBodyMap, parameterName);
            if (StringUtils.isNotBlank(resultTagValue)) {
                oriTags[i] = resultTagValue;
                continue;
            }
            // 再从header取值
            resultTagValue = HttpUtil.getMapValueIgnoreCase(headersMap, parameterName);
            if (StringUtils.isNotBlank(resultTagValue)) {
                oriTags[i] = resultTagValue;
            }
        }
        logParams.setUserTags(oriTags);
    }

    private JSONObject formatRequestInfo(boolean isMultipart, HttpServletRequest request, Map<String, String> requestHeaderMap) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        String requestBodyParams = isMultipart ? "Binary data" : ((RequestWrapper) request).getBodyString();
        return HttpRequestData.of1(request.getRequestURI(), requestBodyParams, parameterMap, requestHeaderMap).toJSON();
    }

    private static String getResponseBody(ContentCachingResponseWrapper response) {
        Map<String, String> responseHeaders = getResponseHeaders(response);
        if (HttpUtil.isDownstream(responseHeaders)) {
            return "Binary data";
        }
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length == 0) {
                return null;
            }
            String payload;
            // 限制length字节数组大于5w时不解析
            if (buf.length > 50000) {
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
        String jnsHeader = HttpUtil.getMapValueIgnoreCase(headerMap, JIDU_JNS_HEADER);
        if (StringUtils.isNotBlank(jnsHeader)) {
            return LogPoint.feign_server;
        }
        String userAgent = HttpUtil.getMapValueIgnoreCase(headerMap, USER_AGENT);
        if (StringUtils.isBlank(userAgent)) {
            return LogPoint.unknown;
        }
        UserAgentType userAgentType = UaUtil.parseUserAgentType(userAgent);
        if (UserAgentType.LIBRARY.equals(userAgentType)) {
            return LogPoint.feign_server;
        }
        return LogPoint.http_server;
    }

    private static class RequestInfo {
        Map<String, String> requestHeaderMap;
        HandlerMethod method;
        LogPoint logPoint;
        boolean isMultipart;
        HttpServletRequest wrappedRequest;
    }
}
