package com.jiduauto.monilog;

import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.UserAgentType;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.apache.commons.lang3.StringUtils;

class UaUtil {
    private static final UserAgentStringParser PARSER = UADetectorServiceFactory.getResourceModuleParser();
    public static UserAgentType parseUserAgentType(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return UserAgentType.UNKNOWN;
        }
        return PARSER.parse(userAgent).getType();
    }

}
