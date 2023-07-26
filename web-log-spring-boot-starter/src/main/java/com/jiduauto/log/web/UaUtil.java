package com.jiduauto.log.web;

import com.jiduauto.log.core.enums.LogPoint;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.UserAgentType;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

class UaUtil {
    private static final UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();

    public static ReadableUserAgent parseUserAgent(String userAgent) { return parser.parse (userAgent);}

    public static UserAgentType parseUserAgentType(String userAgent) { return parser.parse (userAgent).getType();}

    public static LogPoint validateRequest(Map<String, String> headerMap) {
        // 为空返回不知道
        if (MapUtils.isEmpty(headerMap)) {
            return LogPoint.UNKNOWN_ENTRY;
        }
        if (MapUtils.isEmpty(headerMap)) {
            return LogPoint.UNKNOWN_ENTRY;
        }
        if (headerMap.containsKey(WebLogConstant.JIDU_JNS_HEADER)) {
            return LogPoint.RPC_ENTRY;
        }
        String userAgent = getUserAgent(headerMap);
        if (StringUtils.isBlank(userAgent)) {
            return LogPoint.UNKNOWN_ENTRY;
        }
        UserAgentType userAgentType = UaUtil.parseUserAgentType(userAgent);
        if (UserAgentType.LIBRARY.equals(userAgentType)) {
            return LogPoint.RPC_ENTRY;
        }
        // 在这里写入具体的HTTP请求校验逻辑
        return LogPoint.WEB_ENTRY;
    }

    private static String getUserAgent(Map<String, String> headerMap){
        String userAgent = headerMap.get(WebLogConstant.USER_AGENT);
        if (StringUtils.isNotBlank(userAgent)) {
            return userAgent;
        }
        // 全小写
        userAgent = headerMap.get(WebLogConstant.USER_AGENT.toLowerCase());
        if (StringUtils.isNotBlank(userAgent)) {
            return userAgent;
        }
        // 全大写
        return headerMap.get(WebLogConstant.USER_AGENT.toUpperCase());
    }

    private static void checkUa(String ua){
        ReadableUserAgent readableUserAgent = parseUserAgent(ua);
        UserAgentType type = readableUserAgent.getType();
        System.out.println(type);
    }

    public static void main(String[] args) {
        String ua ="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36";
        checkUa(ua);

        ua ="Apifox/1.0.0 (https://www.apifox.cn)";
        checkUa(ua);

        ua = "Apache-HttpClient/4.5.12 (Java/1.8.0_151)";
        checkUa(ua);
    }

}
