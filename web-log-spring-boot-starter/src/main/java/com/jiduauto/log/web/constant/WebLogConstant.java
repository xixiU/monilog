package com.jiduauto.log.web.constant;

/**
 * @description: web侧常量
 * @author rongjie.yuan
 * @date 2023/7/19 15:41
 */
public final class WebLogConstant {
    // web相关常量
    public static final String URI = "uri";

    /**
     * http的方法
     */
    public static final String METHOD ="method";

    /**
     * 请求头
     */
    public static final String HEADER ="header";

    public static final String APPLICATION_JSON = "application/json";


    /**
     * 健康检查地址
     */
    public static final String MISC_PING_URL = "/misc/ping";

    /**
     * 集度JNS请求时header中会带X-JIDU-SERVICENAME
     */
    public static final String JIDU_JNS_HEADER ="X-JIDU-SERVICENAME";

    public static final String USER_AGENT ="User-Agent";


}
