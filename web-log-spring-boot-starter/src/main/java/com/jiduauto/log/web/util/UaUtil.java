package com.jiduauto.log.web.util;

import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.UserAgentType;
import net.sf.uadetector.service.UADetectorServiceFactory;

public class UaUtil {
    private static final UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();

    public static ReadableUserAgent parseUserAgent(String userAgent) { return parser.parse (userAgent);}

    public static UserAgentType parseUserAgentType(String userAgent) { return parser.parse (userAgent).getType();}


    public static void main(String[] args) {
        String ua ="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36";
        checkUa(ua);

        ua ="Apifox/1.0.0 (https://www.apifox.cn)";
        checkUa(ua);

        ua = "Apache-HttpClient/4.5.12 (Java/1.8.0_151)";
        checkUa(ua);
    }

    private static void checkUa(String ua){
        ReadableUserAgent readableUserAgent = parseUserAgent(ua);
        UserAgentType type = readableUserAgent.getType();
        System.out.println(type);
    }
}
