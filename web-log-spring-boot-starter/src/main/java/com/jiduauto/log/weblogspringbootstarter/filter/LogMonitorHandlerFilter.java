package com.jiduauto.log.weblogspringbootstarter.filter;

import com.alibaba.fastjson.JSON;
import com.jiduauto.log.constant.Constants;
import com.jiduauto.log.enums.LogPoint;
import com.jiduauto.log.model.MonitorLogParams;
import com.jiduauto.log.util.MonitorUtil;
import com.jiduauto.log.weblogspringbootstarter.model.DataResponse;
import com.jiduauto.log.weblogspringbootstarter.util.UrlMatcherUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
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
public class LogMonitorHandlerFilter extends OncePerRequestFilter {

    /**
     * 黑名单，不打印日志
     */
    @Value("${monitor.web.blackList}")
    private List<String> BLACK_LIST;


    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws IOException, ServletException {
        if (CollectionUtils.isEmpty(BLACK_LIST)) {
            BLACK_LIST = Collections.singletonList(Constants.MISC_PING_URL);
        }

        if (UrlMatcherUtils.checkContains(BLACK_LIST, request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        HandlerMethod method = (HandlerMethod) request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler");

        long startTime = System.currentTimeMillis();
        String responseBodyStr = "";
        MonitorLogParams logParams = new MonitorLogParams();
        logParams.setLogPoint(LogPoint.WEB_ENTRY);
        logParams.setServiceCls(method.getMethod().getClass());
        logParams.setService(getClassName(request));
        logParams.setAction(getMethodName(request));

        try {
            ContentCachingRequestWrapper wrapperRequest = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper wrapperResponse = new ContentCachingResponseWrapper(response);
            // 记录下请求内容,响应时间
            log.info("REQUEST:URI={},METHOD={},P={},HEADERS={},PARAMS={}", wrapperRequest.getRequestURI(),
                    wrapperRequest.getMethod(), wrapperRequest.getRemoteAddr(),
                    JSON.toJSONString(getHeaders(request)),
                    JSON.toJSONString(wrapperRequest.getParameterMap()));
            filterChain.doFilter(wrapperRequest, wrapperResponse);
            String requestBodyStr = getRequestBody(wrapperRequest);
//            logParams.setInput(Arrays.asList(requestBodyStr));

            log.info("REQUEST:URI={},METHOD={}, body = {}", wrapperRequest.getRequestURI(),
                    wrapperRequest.getMethod(), requestBodyStr);
            responseBodyStr = getResponseBody(wrapperResponse);
            wrapperResponse.copyBodyToResponse();
            logParams.setOutput(responseBodyStr);
            logParams.setSuccess(true);
        } catch (Exception e) {
            log.warn("RESPONSE:ERROR:URI:{},FilterChainException:" + e.getMessage(), request.getRequestURI());
            log.warn("caught Exception: quit filter chain, send out response.", e);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            DataResponse<String> errorResponse = new DataResponse<>().setData(
                    StringUtils.defaultIfBlank(e.getMessage(), "Internal Server Error")).fail();
            response.getWriter().write(JSON.toJSONString(errorResponse));
            logParams.setSuccess(false);
            logParams.setException(e);
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            logParams.setCost(cost);
            log.info("RESPONSE:URI={} cost {} ms, result={}", request.getRequestURI(),
                        cost, responseBodyStr);
            MonitorUtil.log(logParams);
        }
    }

    private String getClassName(HttpServletRequest request) {
        return request.getServletPath();
    }

    private String getMethodName(HttpServletRequest request) {
        return request.getMethod();
    }


    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>(32);
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            String value = request.getHeader(key);
            map.put(key, value);
        }
        return map;
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
