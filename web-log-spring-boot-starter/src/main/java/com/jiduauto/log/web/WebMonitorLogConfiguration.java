package com.jiduauto.log.web;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.jiduauto.log.core.CoreMonitorLogConfiguration;
import com.jiduauto.log.core.annotation.MonitorLogTags;
import com.jiduauto.log.core.constant.Constants;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.uadetector.UserAgentType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;


@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "monitor.log.web", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.include:*}'.equals('*') or '${monitor.log.component.include}'.contains('web')) and !('${monitor.log.component.exclude:}'.equals('*') or '${monitor.log.component.exclude:}'.contains('web'))")
@ConditionalOnClass({CoreMonitorLogConfiguration.class})
@Slf4j
class WebMonitorLogConfiguration extends OncePerRequestFilter {
    /**
     * 集度JNS请求时header中会带X-JIDU-SERVICENAME
     */
    public static final String JIDU_JNS_HEADER = "X-JIDU-SERVICENAME";
    public static final String USER_AGENT = "User-Agent";
    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();
    /**
     * 不监控的url清单，支持模糊路径如a/*
     */
    @Value("${monitor.log.web.blackList:[/actuator/health, /misc/ping, /actuator/prometheus]}")
    private List<String> blackList;

    @Bean
    FilterRegistrationBean<WebMonitorLogConfiguration> logMonitorFilterBean() {
        FilterRegistrationBean<WebMonitorLogConfiguration> filterRegBean = new FilterRegistrationBean<>();
        filterRegBean.setFilter(this);
        filterRegBean.setOrder(Integer.MAX_VALUE);
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("logMonitorFilter");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }

    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws IOException, ServletException {
        String requestURI = request.getRequestURI();
        if (CollectionUtils.isEmpty(blackList)) {
            blackList = Lists.newArrayList();
        }
        if (checkUrlMatch(blackList, requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }
        HandlerMethod method = getHandlerMethod(request);
        if (method == null) {
            filterChain.doFilter(request, response);
            return;
        }
        List<String> tagList = new ArrayList<>();
        MonitorLogTags logTags = ReflectUtil.getAnnotation(MonitorLogTags.class, method.getBeanType(), method.getMethod());
        if (logTags != null && logTags.tags() != null) {
            if (logTags.tags().length % 2 == 0) {
                tagList = new ArrayList<>(Arrays.asList(logTags.tags()));
            } else {
                log.error("tags length must be double，method：{}", method.getMethod().getName());
            }
        }

        long startTime = System.currentTimeMillis();
        String responseBodyStr = "";
        MonitorLogParams logParams = new MonitorLogParams();
        logParams.setLogPoint(LogPoint.WEB_ENTRY);
        logParams.setServiceCls(method.getBeanType());
        logParams.setService(method.getBeanType().getSimpleName());
        logParams.setAction(method.getMethod().getName());
        TagBuilder tagBuilder = TagBuilder.of(tagList).add("uri", requestURI).add("method", request.getMethod());
        logParams.setTags(tagBuilder.toArray());

        Map<String, String> requestHeaderMap = getRequestHeaders(request);
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        ContentCachingRequestWrapper wrapperRequest = isMultipart ? null : new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrapperResponse = new ContentCachingResponseWrapper(response);

        String requestBodyParams = getRequestBodyParams(wrapperRequest, requestHeaderMap);

        logParams.setLogPoint(validateRequest(requestHeaderMap));
        logParams.setInput(new Object[]{formatRequestInfo(request, requestHeaderMap, requestBodyParams)});
        //TODO 增加LogParser注解

        try {
            dealRequestTags(wrapperRequest, logParams, requestHeaderMap, requestBodyParams);
        } catch (Exception e) {
            log.error("dealRequestTags error", e);
        }
        try {
            filterChain.doFilter(wrapperRequest, wrapperResponse);
            responseBodyStr = getResponseBody(wrapperResponse);
            wrapperResponse.copyBodyToResponse();
            logParams.setOutput(responseBodyStr);
            logParams.setSuccess(true);
        } catch (Exception e) {
            logParams.setSuccess(false);
            logParams.setException(e);
            throw e;
        } finally {
            if (logParams.isSuccess() && StringUtils.isNotBlank(responseBodyStr) && isJson(requestHeaderMap)) {
                dealResponseTags(logParams, responseBodyStr);
            }

            long cost = System.currentTimeMillis() - startTime;
            logParams.setCost(cost);
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
        Map<String, HandlerMapping> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(MonitorSpringUtils.getApplicationContext(), HandlerMapping.class, true, false);
        List<HandlerMapping> handlerMappings = new ArrayList<>(matchingBeans.values());
        for (HandlerMapping mapping : handlerMappings) {
            HandlerExecutionChain handlerExecutionChain;
            try {
                handlerExecutionChain = mapping.getHandler(request);
            } catch (Exception e) {
                log.error("getHandler error", e);
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
     * @param logParams
     */
    private void dealResponseTags(MonitorLogParams logParams, String responseBodyStr) {
        String[] oriTags = logParams.getTags();

        HashMap<String, String> jsonMap = MonitorStringUtil.tryConvert2Map(responseBodyStr);
        if (MapUtils.isEmpty(jsonMap)) {
            return;
        }
        for (int i = 0; oriTags != null && i < oriTags.length; i++) {
            if (!oriTags[i].startsWith("{") || !oriTags[i].endsWith("}")) {
                continue;
            }
            String parameterName = oriTags[i].substring(1, oriTags[i].length() - 1);
            String resultTagValue = jsonMap.get(parameterName);
            if (StringUtils.isBlank(resultTagValue)) {
                continue;
            }
            oriTags[i] = resultTagValue;
        }
        logParams.setTags(oriTags);
    }

    /**
     * 处理请求tag
     *
     * @param request
     * @param logParams
     */
    private void dealRequestTags(ContentCachingRequestWrapper request, MonitorLogParams logParams, Map<String, String> requestHeaderMap, String requestBodyParams) {
        String[] oriTags = logParams.getTags();
        Map<String, String> headersMap = MapUtils.isNotEmpty(requestHeaderMap) ? requestHeaderMap : new HashMap<>();
        HashMap<String, String> requestBodyMap = MonitorStringUtil.tryConvert2Map(requestBodyParams);

        HashMap<String, String> requestBody = MapUtils.isNotEmpty(requestBodyMap) ? requestBodyMap : new HashMap<>();

        for (int i = 0; oriTags != null && i < oriTags.length; i++) {
            if (!oriTags[i].startsWith("{") || !oriTags[i].endsWith("}")) {
                continue;
            }
            String parameterName = oriTags[i].substring(1, oriTags[i].length() - 1);
            // 先从url参数取值
            String resultTagValue = request.getParameter(parameterName);

            if (StringUtils.isNotBlank(resultTagValue)) {
                swapTag(oriTags, i, resultTagValue);
                continue;
            }
            // 再从body中取值
            resultTagValue = getMapValueIgnoreCase(requestBody, parameterName);
            if (StringUtils.isNotBlank(resultTagValue)) {
                swapTag(oriTags, i, resultTagValue);
                continue;
            }
            // 再从header取值
            resultTagValue = getMapValueIgnoreCase(headersMap, parameterName);
            if (StringUtils.isNotBlank(resultTagValue)) {
                swapTag(oriTags, i, resultTagValue);
            }
        }
        logParams.setTags(oriTags);
    }

    private static void swapTag(String[] oriTags, int index, String resultTagValue) {
        swapTag(oriTags, index, resultTagValue, false);
    }

    private static void swapTag(String[] oriTags, int index, String resultTagValue, boolean valueEmptySkip) {
        if (valueEmptySkip && StringUtils.isBlank(resultTagValue)) {
            return;
        }
        resultTagValue = StringUtils.isNotBlank(resultTagValue) ? resultTagValue : Constants.NO_VALUE_CODE;
        oriTags[index] = resultTagValue;
    }


    private static String getRequestBody(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper != null) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                String payload;
                try {
                    payload = new String(buf, 0, buf.length, wrapper.getCharacterEncoding());
                } catch (UnsupportedEncodingException e) {
                    payload = "[unknown]";
                }
                return payload.replaceAll("\\n", "");
            }
        }
        return "";
    }

    private String getRequestBodyParams(HttpServletRequest request, Map<String, String> requestHeaderMap) {
        return isDownstream(requestHeaderMap) ? "Binary data" : getRequestBody(request);
    }

    private JSONObject formatRequestInfo(HttpServletRequest request, Map<String, String> requestHeaderMap, String requestBodyParams) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        JSONObject obj = new JSONObject();
        if (StringUtils.isNotBlank(requestBodyParams)) {
            HashMap<String, String> requestBodyMap = MonitorStringUtil.tryConvert2Map(requestBodyParams);
            obj.put("body", requestBodyMap != null ? requestBodyMap : requestBodyParams);
        }
        if (MapUtils.isNotEmpty(parameterMap)) {
            Map<String, Collection<String>> collected = parameterMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, item -> Arrays.asList(item.getValue())));
            obj.put("query", MonitorStringUtil.encodeQueryString(collected));
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
        return "";
    }

    private static boolean checkUrlMatch(List<String> urls, String url) {
        for (String pattern : urls) {
            if (antPathMatcher.match(pattern, url)) {
                return true;
            }
        }
        return false;
    }


    private static LogPoint validateRequest(Map<String, String> headerMap) {
        // 为空返回不知道
        if (MapUtils.isEmpty(headerMap)) {
            return LogPoint.UNKNOWN_ENTRY;
        }
        if (headerMap.containsKey(JIDU_JNS_HEADER)) {
            return LogPoint.RPC_ENTRY;
        }
        String userAgent = getMapValueIgnoreCase(headerMap, USER_AGENT);
        if (StringUtils.isBlank(userAgent)) {
            return LogPoint.UNKNOWN_ENTRY;
        }
        UserAgentType userAgentType = UaUtil.parseUserAgentType(userAgent);
        if (UserAgentType.LIBRARY.equals(userAgentType)) {
            return LogPoint.RPC_ENTRY;
        }
        return LogPoint.WEB_ENTRY; // 在这里写入具体的HTTP请求校验逻辑
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
