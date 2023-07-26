package com.jiduauto.log.web;

import com.jiduauto.log.core.annotation.MonitorLogTags;
import com.jiduauto.log.core.constant.Constants;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.jiduauto.log.core.util.ReflectUtil;
import com.jiduauto.log.core.util.SpringUtils;
import com.jiduauto.log.core.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
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

/**
 * 请求处理过滤器
 *
 * @author runqian.wang
 * @version 1.0.0
 * @since 2022/7/26
 */
@Slf4j
class LogMonitorHandlerFilter extends OncePerRequestFilter {
    /**
     * 不监控的日url清单，支持模糊路径如a/*
     */
    @Value("${monitor.log.web.blackList}")
    private List<String> BLACK_LIST;

    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                 @NonNull FilterChain filterChain) throws IOException, ServletException {
        String requestURI = request.getRequestURI();
        if (CollectionUtils.isEmpty(BLACK_LIST)) {
            BLACK_LIST = Collections.singletonList(WebLogConstant.MISC_PING_URL);
        }

        if (UrlMatcherUtils.checkUrlMatch(BLACK_LIST, requestURI)) {
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
                // 非偶数tag prometheus上报会报错，这里只打一行日志提醒
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
        tagList.add(WebLogConstant.URI);
        tagList.add(requestURI);
        tagList.add(WebLogConstant.METHOD);
        tagList.add(request.getMethod());
        logParams.setTags(tagList.toArray(new String[0]));

        Map<String, String> headerMap = getHeaders(request);
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        ContentCachingRequestWrapper wrapperRequest = isMultipart ? null : new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrapperResponse = new ContentCachingResponseWrapper(response);
        logParams.setLogPoint(UaUtil.validateRequest(headerMap));

        try{
            dealRequestTags(wrapperRequest, logParams);
        }catch (Exception e){
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
            if (logParams.isSuccess() && StringUtils.isNotBlank(responseBodyStr) && isJson(headerMap)) {
                dealResponseTags(responseBodyStr, logParams);
            }

            long cost = System.currentTimeMillis() - startTime;
            logParams.setCost(cost);
            MonitorLogUtil.log(logParams);
        }
    }

    private  Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>(32);
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            String value = request.getHeader(key);
            map.put(key, value);
        }
        return map;
    }
    private boolean isJson(Map<String, String> headerMap) {
        if (MapUtils.isEmpty(headerMap)) {
            return false;
        }

        if (isDownstream(headerMap)) {
            return false;
        }
        String header = UaUtil.getMapValueIgnoreCase(headerMap, HttpHeaders.CONTENT_TYPE);
        return StringUtils.containsIgnoreCase(header, MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean isDownstream(Map<String, String> headerMap) {
        String header = UaUtil.getMapValueIgnoreCase(headerMap, HttpHeaders.CONTENT_DISPOSITION);
        return StringUtils.containsIgnoreCase(header, "attachment")
                || StringUtils.containsIgnoreCase(header, "filename");
    }


    private HandlerMethod getHandlerMethod(HttpServletRequest request) {
        Map<String, HandlerMapping> matchingBeans =
                BeanFactoryUtils.beansOfTypeIncludingAncestors(SpringUtils.getApplicationContext(),
                        HandlerMapping.class, true, false);
        List<HandlerMapping> handlerMappings = new ArrayList<>(matchingBeans.values());
        for (HandlerMapping mapping : handlerMappings) {
            HandlerExecutionChain handlerExecutionChain = null;
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
     * @param responseBodyStr
     * @param logParams
     */
    private void dealResponseTags(String responseBodyStr, MonitorLogParams logParams) {
        String[] oriTags = logParams.getTags();

        HashMap<String, String> jsonMap = StringUtil.tryConvert2Map(responseBodyStr);
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
    private void dealRequestTags(ContentCachingRequestWrapper request, MonitorLogParams logParams) {
        String[] oriTags = logParams.getTags();
        Map<String, String> headersMap = getHeaders(request);

        HashMap<String, String> requestBody = StringUtil.tryConvert2Map(getRequestBody(request));

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
            resultTagValue = UaUtil.getMapValueIgnoreCase(requestBody, parameterName);
            if (StringUtils.isNotBlank(resultTagValue)) {
                swapTag(oriTags, i, resultTagValue);
                continue;
            }
            // 再从header取值
            resultTagValue = UaUtil.getMapValueIgnoreCase(headersMap, parameterName);
            swapTag(oriTags, i, resultTagValue);

        }
        logParams.setTags(oriTags);
    }

    private void swapTag(String[] oriTags, int index, String resultTagValue) {
        resultTagValue = StringUtils.isNotBlank(resultTagValue) ? resultTagValue : Constants.NO_VALUE_CODE;
        oriTags[index] = resultTagValue;
    }


    private String getRequestBody(ContentCachingRequestWrapper request) {
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

    private String getResponseBody(ContentCachingResponseWrapper response) {
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response,
                ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                String payload;
                try {
                    payload = new String(buf, 0, buf.length, wrapper.getCharacterEncoding());
                } catch (UnsupportedEncodingException e) {
                    payload = "[unknown]";
                }
                return payload;
            }
        }
        return "";
    }
}
