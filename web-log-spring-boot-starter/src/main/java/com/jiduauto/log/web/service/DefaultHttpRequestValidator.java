package com.jiduauto.log.web.service;

import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.web.constant.WebLogConstant;
import com.jiduauto.log.web.util.HttpUtil;
import com.jiduauto.log.web.util.UaUtil;
import net.sf.uadetector.UserAgentType;
import org.apache.commons.collections4.MapUtils;

import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Map;

public class DefaultHttpRequestValidator implements HttpRequestValidator {
    @Override
    public LogPoint validateRequest(HttpServletRequestWrapper requestWrapper) {
        // 为空返回不知道
        if (requestWrapper == null) {
           return LogPoint.UNKNOWN_ENTRY;
        }
        Map<String, String> headerMap = HttpUtil.getHeaders(requestWrapper);
        if (MapUtils.isEmpty(headerMap)) {
            return LogPoint.UNKNOWN_ENTRY;
        }
        if (headerMap.containsKey(WebLogConstant.JIDU_JNS_HEADER)) {
            return LogPoint.REMOTE_CLIENT;
        }
        String userAgent = headerMap.get(WebLogConstant.USER_AGENT);
        UserAgentType userAgentType = UaUtil.parseUserAgentType(userAgent);
        if (UserAgentType.LIBRARY.equals(userAgentType)) {
            return LogPoint.REMOTE_CLIENT;
        }
        // 在这里写入具体的HTTP请求校验逻辑
        return LogPoint.UNKNOWN_ENTRY;
    }


}