package com.jiduauto.monitor.log;

import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.UserAgentType;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.apache.commons.lang3.StringUtils;

class UaUtil {
    private static final UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();

    public static UserAgentType parseUserAgentType(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return UserAgentType.UNKNOWN;
        }
        return parser.parse(userAgent).getType();
    }

}
