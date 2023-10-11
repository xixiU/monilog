package com.jiduauto.monilog;

import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.UserAgentType;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

class UaUtil {
    /**
     * 添加缓存
     */
    private static final ConcurrentHashMap<String, UserAgentType> CACHE = new ConcurrentHashMap<>();

    private static final UserAgentStringParser PARSER = UADetectorServiceFactory.getResourceModuleParser();
    public static UserAgentType parseUserAgentType(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return UserAgentType.UNKNOWN;
        }

        // 检查缓存中是否已经存在解析结果
        if (CACHE.containsKey(userAgent)) {
            return CACHE.get(userAgent);
        }
        UserAgentType userAgentType = PARSER.parse(userAgent).getType();
        // 将解析结果放入缓存
        CACHE.put(userAgent, userAgentType);
        return userAgentType;
    }

}
