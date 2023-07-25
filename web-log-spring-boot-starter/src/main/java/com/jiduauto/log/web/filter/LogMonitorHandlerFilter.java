package com.jiduauto.log.web.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONValidator;
import com.alibaba.fastjson.TypeReference;
import com.jiduauto.log.core.annotation.MonitorLogTags;
import com.jiduauto.log.core.constant.Constants;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.jiduauto.log.core.util.ReflectUtil;
import com.jiduauto.log.core.util.SpringUtils;
import com.jiduauto.log.web.WebLogConstant;
import com.jiduauto.log.web.model.DataResponse;
import com.jiduauto.log.web.util.UrlMatcherUtils;
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
public class LogMonitorHandlerFilter extends OncePerRequestFilter {

    /**
     * 黑名单，不打印日志
     */
    @Value("${monitor.web.blackList}")
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

        MonitorLogTags logTags = ReflectUtil.getAnnotation(MonitorLogTags.class, method.getBeanType() , method.getMethod());
        if (logTags != null && logTags.tags() != null && logTags.tags().length %2 ==0) {
            tagList = Arrays.asList(logTags.tags());
        }else{
            // 非偶数tag prometheus上报会报错，这里只打一行日志提醒
            log.error("tags length must be double，method：{}", method.getMethod().getName());
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
        dealRequestTags(request, logParams);

        Map<String, String> headerMap = getHeaders(request);
        try {
            boolean isMultipart = ServletFileUpload.isMultipartContent(request);
            ContentCachingRequestWrapper wrapperRequest = isMultipart ? null : new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper wrapperResponse = new ContentCachingResponseWrapper(response);

            String requestParams = isMultipart ? "{}" : JSON.toJSONString(wrapperRequest.getParameterMap());
            // 记录下请求内容,响应时间
            log.info("REQUEST:URI={},METHOD={},P={},HEADERS={},PARAMS={}", wrapperRequest.getRequestURI(),
                    wrapperRequest.getMethod(), wrapperRequest.getRemoteAddr(), JSON.toJSONString(headerMap), requestParams);
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
            log.warn("RESPONSE:ERROR:URI:{},FilterChainException:" + e.getMessage(), requestURI);
            log.warn("caught Exception: quit filter chain, send out response.", e);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            DataResponse<String> errorResponse = new DataResponse<>().setData(
                    StringUtils.defaultIfBlank(e.getMessage(), "Internal Server Error")).fail();
            response.getWriter().write(JSON.toJSONString(errorResponse));
            logParams.setSuccess(false);
            logParams.setException(e);
        } finally {
            if (logParams.isSuccess() && StringUtils.isNotBlank(responseBodyStr) && isJson(headerMap)) {
                dealResponseTags(responseBodyStr, logParams);
            }
            long cost = System.currentTimeMillis() - startTime;
            logParams.setCost(cost);
            log.info("RESPONSE:URI={} cost {} ms, result={}", requestURI, cost, responseBodyStr);
            MonitorLogUtil.log(logParams);
        }
    }

    private boolean isJson(Map<String, String> headerMap) {
        if (MapUtils.isEmpty(headerMap)) {
            return false;
        }

        if (isDownstream(headerMap)) {
            return false;
        }
        String header = getHeaderValue(headerMap, HttpHeaders.CONTENT_TYPE);
        return StringUtils.containsIgnoreCase(header, MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean isDownstream(Map<String, String> headerMap) {
        String header = getHeaderValue(headerMap, HttpHeaders.CONTENT_DISPOSITION);
        return StringUtils.containsIgnoreCase(header, "attachment")
                || StringUtils.containsIgnoreCase(header, "filename");
    }

    private String getHeaderValue(Map<String, String> headerMap, String headerKey){
        if (MapUtils.isEmpty(headerMap) || StringUtils.isBlank(headerKey)) {
            return null;
        }
        return headerMap.get(headerKey);
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
                log.error("getHandler error" ,e);
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
     * @param responseBodyStr
     * @param logParams
     */
    private void dealResponseTags(String responseBodyStr, MonitorLogParams logParams) {
        String[] oriTags = logParams.getTags();
        boolean validate = JSONValidator.from(responseBodyStr).validate();
        if (!validate) {
            return;
        }
        HashMap<String, String> jsonMap = JSON.parseObject(responseBodyStr, new TypeReference<HashMap<String, String>>() {
        });
        for (int i = 0; oriTags!= null && i < oriTags.length; i++) {
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
     * @param request
     * @param logParams
     */
    private void dealRequestTags(HttpServletRequest request, MonitorLogParams logParams) {
        String[] oriTags = logParams.getTags();
        for (int i = 0; oriTags!= null && i < oriTags.length; i++) {
            if (!oriTags[i].startsWith("{") || !oriTags[i].endsWith("}")) {
                continue;
            }
            String parameterName = oriTags[i].substring(1, oriTags[i].length() - 1);
            String resultTagValue = request.getParameter(parameterName);
//            if (StringUtils.isBlank(resultTagValue) && (request instanceof RequestWrapper)) {
//                String bodyString = ((RequestWrapper) request).getBodyString();
//                JSONObject bodyJson = JSONObject.parseObject(bodyString);
//                resultTagValue = bodyJson == null ? resultTagValue : bodyJson.getString(parameterName);
//            }
            if (StringUtils.isBlank(resultTagValue)) {
                continue;
            }
            resultTagValue = StringUtils.isNotBlank(resultTagValue) ? resultTagValue : Constants.NO_VALUE_CODE;
            oriTags[i] = resultTagValue;
        }
        logParams.setTags(oriTags);
//        return oriTags;
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
