package com.jiduauto.monitor.log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.uadetector.UserAgentType;
import org.apache.commons.collections4.CollectionUtils;
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

import javax.annotation.Nullable;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

import static com.jiduauto.monitor.log.StringUtil.checkPathMatch;


@Slf4j
@AllArgsConstructor
class WebMonitorLogInterceptor extends OncePerRequestFilter {
    /**
     * 集度JNS请求时header中会带X-JIDU-SERVICENAME
     */
    private static final String JIDU_JNS_HEADER = "X-JIDU-SERVICENAME";
    private static final String USER_AGENT = "User-Agent";
    private final MonitorLogProperties monitorLogProperties;

    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws IOException, ServletException {
        MonitorLogProperties.WebProperties webProperties = monitorLogProperties.getWeb();
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        @Nullable
        RequestWrapper wrapperRequest = isMultipart ? null : new RequestWrapper(request);
        String requestURI = request.getRequestURI();

        Set<String> urlBlackList = webProperties.getUrlBlackList();
        if (CollectionUtils.isEmpty(urlBlackList)) {
            urlBlackList = new HashSet<>();
        }
        if (checkPathMatch(urlBlackList, requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }
        HandlerMethod method = getHandlerMethod(isMultipart ? request : wrapperRequest);
        if (method == null) {
            filterChain.doFilter(request, response);
            return;
        }
        ContentCachingResponseWrapper wrapperResponse = new ContentCachingResponseWrapper(response);

        MonitorLogTags logTags = ReflectUtil.getAnnotation(MonitorLogTags.class, method.getBeanType(), method.getMethod());
        List<String> tagList = StringUtil.getTagList(logTags);
        long startTime = System.currentTimeMillis();
        String responseBodyStr = "";
        MonitorLogParams logParams = new MonitorLogParams();
        if (tagList != null && tagList.size() > 1) {
            logParams.setHasUserTag(true);
        }
        logParams.setServiceCls(method.getBeanType());
        logParams.setService(method.getBeanType().getSimpleName());
        logParams.setAction(method.getMethod().getName());
        TagBuilder tagBuilder = TagBuilder.of(tagList).add("url", requestURI).add("method", request.getMethod());
        logParams.setTags(tagBuilder.toArray());

        Map<String, String> requestHeaderMap = getRequestHeaders(request);

        Map<String, String> requestBodyMap = new HashMap<>();
        logParams.setLogPoint(validateRequest(requestHeaderMap));
        JSONObject jsonObject = formatRequestInfo(isMultipart, isMultipart ? request : wrapperRequest, requestHeaderMap);
        Object o = jsonObject.get("body");
        if (o instanceof Map) {
            requestBodyMap.putAll((Map<String, String>) o);
        }
        logParams.setInput(new Object[]{jsonObject});
        logParams.setMsgCode(ErrorEnum.SUCCESS.name());
        logParams.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
        logParams.setSuccess(true);

        try {
            dealRequestTags(isMultipart ? request : wrapperRequest, logParams, requestHeaderMap, requestBodyMap);
        } catch (Exception e) {
            MonitorLogUtil.log("dealRequestTags error: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(isMultipart ? request : wrapperRequest, wrapperResponse);
            responseBodyStr = getResponseBody(wrapperResponse);
            wrapperResponse.copyBodyToResponse();
            JSON json = StringUtil.tryConvert2Json(responseBodyStr);
            if (json instanceof JSONObject) {
                LogParser cl = ReflectUtil.getAnnotation(LogParser.class, method.getBeanType(), method.getMethod());
                //尝试更精确的提取业务失败信息
                ResultParseStrategy rps = cl == null ? null : cl.resultParseStrategy();//默认使用IfSuccess策略
                String boolExpr = cl == null ? null : cl.boolExpr();
                String codeExpr = cl == null ? null : cl.errorCodeExpr();
                String msgExpr = cl == null ? null : cl.errorMsgExpr();
                ParsedResult pr = ResultParseUtil.parseResult(json, rps, null, boolExpr, codeExpr, msgExpr);
                logParams.setSuccess(pr.isSuccess());
                logParams.setMsgCode(pr.getMsgCode());
                logParams.setMsgInfo(pr.getMsgInfo());
                logParams.setOutput(json);
            } else {
                logParams.setOutput(responseBodyStr);
                logParams.setSuccess(true);
            }
        } catch (Exception e) {
            logParams.setSuccess(false);
            logParams.setException(e);
            ErrorInfo errorInfo = ExceptionUtil.parseException(e);
            logParams.setMsgCode(errorInfo.getErrorCode());
            logParams.setMsgInfo(errorInfo.getErrorMsg());
            throw e;
        } finally {
            if (logParams.isSuccess() && StringUtils.isNotBlank(responseBodyStr) && isJson(requestHeaderMap)) {
                dealResponseTags(logParams, responseBodyStr);
            }
            logParams.setCost(System.currentTimeMillis() - startTime);
            MonitorLogUtil.log(logParams);
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
        Map<String, HandlerMapping> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(SpringUtils.getApplicationContext(), HandlerMapping.class, true, false);
        List<HandlerMapping> handlerMappings = new ArrayList<>(matchingBeans.values());
        for (HandlerMapping mapping : handlerMappings) {
            HandlerExecutionChain handlerExecutionChain;
            try {
                handlerExecutionChain = mapping.getHandler(request);
            } catch (Exception e) {
                MonitorLogUtil.log("getHandlerMethod error: {}", e.getMessage());
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
     * 处理返回的tag
     *
     */
    private void dealResponseTags(MonitorLogParams logParams, String responseBodyStr) {
        String[] oriTags = logParams.getTags();

        Map<String, String> jsonMap = StringUtil.tryConvert2Map(responseBodyStr);
        if (MapUtils.isEmpty(jsonMap)) {
            return;
        }
        for (int i = 0; oriTags != null && i < oriTags.length; i++) {
            if (!oriTags[i].startsWith("{") || !oriTags[i].endsWith("}")) {
                continue;
            }
            String parameterName = oriTags[i].substring(1, oriTags[i].length() - 1);
            String resultTagValue = jsonMap.get(parameterName);
            oriTags[i] = StringUtils.isNotBlank(resultTagValue) ? resultTagValue : "00";
        }
        logParams.setTags(oriTags);
    }

    /**
     * 处理请求tag
     *
     */
    private void dealRequestTags(HttpServletRequest request, MonitorLogParams logParams, Map<String, String> requestHeaderMap, Map<String, String> requestBodyMap) {
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
        String requestBodyParams = isMultipart ? "Binary data" : ((RequestWrapper)request).getBodyString();

        JSONObject obj = new JSONObject();
        if (StringUtils.isNotBlank(requestBodyParams)) {
            Map<String, String> requestBodyMap = StringUtil.tryConvert2Map(requestBodyParams);
            obj.put("body", requestBodyMap != null ? requestBodyMap : requestBodyParams);
        }
        if (MapUtils.isNotEmpty(parameterMap)) {
            Map<String, Collection<String>> collected = parameterMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, item -> Arrays.asList(item.getValue())));
            obj.put("query", StringUtil.encodeQueryString(collected));
        }
        if (MapUtils.isNotEmpty(requestHeaderMap)) {
            obj.put("headers", requestHeaderMap);
        }
        return obj;
    }

    private static String getResponseBody(ContentCachingResponseWrapper response) {
        Map<String, String> responseHeaders = getResponseHeaders(response);
        if (isDownstream(responseHeaders)) {
            return "Binary data";
        }
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                String payload;
                try {
                    payload = new String(buf, wrapper.getCharacterEncoding());
                } catch (UnsupportedEncodingException e) {
                    payload = "[unknown]";
                }
                return payload;
            }
        }
        return null;
    }


    private static LogPoint validateRequest(Map<String, String> headerMap) {
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


    private static String getMapValueIgnoreCase(Map<String, String> headerMap, String headerKey) {
        if (MapUtils.isEmpty(headerMap) || StringUtils.isBlank(headerKey)) {
            return null;
        }
        String userAgent = headerMap.get(headerKey);
        if (StringUtils.isNotBlank(userAgent)) {
            return userAgent;
        }
        // 全小写
        userAgent = headerMap.get(headerKey.toLowerCase());
        if (StringUtils.isNotBlank(userAgent)) {
            return userAgent;
        }
        // 全大写
        return headerMap.get(headerKey.toUpperCase());
    }
}
