package com.example.monilog;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

class HttpUtil {
    private static final String PATH_SEP = "/";
    private static final Set<String> TEXT_TYPES = Sets.newHashSet("application/json", "application/xml", "application/xhtml+xml", "text/");

    private static final Set<String> STREAMING_TYPES = Sets.newHashSet("application/octet-stream", "application/pdf", "application/zip", "application/x-", "application/vnd.", "application/ms", "image/", "audio/", "video/");

    static Boolean checkContentTypeIsStream(String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return null;
        }
        contentType = contentType.toLowerCase();
        for (String textType : TEXT_TYPES) {
            if (contentType.startsWith(textType)) {
                return false;
            }
        }
        for (String streamingType : STREAMING_TYPES) {
            if (contentType.startsWith(streamingType)) {
                return true;
            }
        }
        return null;
    }

    static boolean isDownstream(Map<String, String> headerMap) {
        String header = getMapValueIgnoreCase(headerMap, HttpHeaders.CONTENT_DISPOSITION);
        return StringUtils.containsIgnoreCase(header, "attachment") || StringUtils.containsIgnoreCase(header, "filename");
    }

    static String getMapValueIgnoreCase(Map<String, ?> headerMap, String headerKey) {
        if (MapUtils.isEmpty(headerMap) || StringUtils.isBlank(headerKey)) {
            return null;
        }
        for (String key : headerMap.keySet()) {
            if (StringUtils.equalsIgnoreCase(key, headerKey)) {
                Object v = headerMap.get(key);
                return v == null ? null : v.toString();
            }
        }
        return null;
    }

    static String getFirstHeader(Map<String, Collection<String>> headerMap, String name) {
        if (headerMap == null || StringUtils.isBlank(name)) {
            return null;
        }
        for (Map.Entry<String, Collection<String>> me : headerMap.entrySet()) {
            if (me.getKey().equalsIgnoreCase(name)) {
                Collection<String> headers = me.getValue();
                if (headers == null || headers.isEmpty()) {
                    return null;
                }
                return headers.iterator().next();
            }
        }
        return null;
    }

    /**
     * 提取路径，如http://baidu.com/test?a=1 返回/test,此处针对restful分格的接口也会存在问题，如/getOrder/123
     *
     * @param url url
     * @return 仅路径信息不包含参数与host
     */
    static String extractPath(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        try {
            return URLUtil.getPath(url);
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 从url或uri中提取path路径，对于不可枚举的路径参数，将替换成常量占位符
     */
    static String extractPathWithoutPathParams(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        String path = extractPath(url);
        if (StringUtils.isBlank(path)) {
            return PATH_SEP;
        }
        if (path.indexOf(PATH_SEP) == path.lastIndexOf(PATH_SEP)) {
            return path;
        }
        String[] segments = path.split(PATH_SEP);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (StringUtils.isBlank(segment)) {
                continue;
            }
            if (StringUtil.hasRandomNum(segment)) {
                segments[i] = "{n}";
                continue;
            }
            String[] arr = StringUtil.parseFileName(segment);
            if (StringUtil.isRandomStr(arr[0])) {
                arr[0] = "{xxx}";
            }
            segments[i] = arr[0] + arr[1];
        }
        return StringUtils.join(segments, PATH_SEP);
    }

    public static void main(String[] args) {
        checkRandom("/jiduUpmApi/upm/validateSignInUser",false);
        String str = extractPathWithoutPathParams("/jiduUpmApi/upm/validateSignInUser");
        System.out.println(str);

        checkRandom("JFS-21002",true);
        checkRandom("job", false);
        checkRandom("api", false);
        checkRandom("order-center-platform",false);
        checkRandom("crm-customer-platform-server", false);
        checkRandom("crm-customer-platform", false);
        checkRandom("crm-auth-service", false);
        checkRandom("b-plat-voc", false);
        checkRandom("crm-user-base", false);
        checkRandom("millow", false);
        checkRandom("customer-platform", false);
        checkRandom("jidu_dd96ceac-afc4-431e-8127-72452efa34af.png", true);
        checkRandom("crm-customer-data", false);
        checkRandom("abcdkljlkalksdjfkls", true);
        checkRandom("alkjsdflkjsd", true);
        checkRandom("queryEmployeeInfo", false);
        checkRandom("hello123", false);
        checkRandom("abcdKLljlk20023", true);
        checkRandom("abcdkljlkalksdjfkls", true);
        checkRandom("httpClient", false);
        checkRandom("hello world", false);
        checkRandom("i-love-you", false);
        checkRandom("xxl-job", false);
        checkRandom("ap", false);
        checkRandom("pi", false);
        checkRandom("xxl", false);
        checkRandom(RandomUtil.randomString(10), true);
        System.out.println("over");
    }

    private static void checkRandom(String word, boolean expect) {
        double avgFreq = RandomStringDetector.calcAvgNormalFrequence(word);
        boolean result = avgFreq <= 10.0d;
        boolean exp = result == expect;
        if (exp) {
            System.out.printf("avgFreq: %.2f, %s, word: %s\n", avgFreq, result, word);
        } else {
            System.err.printf("avgFreq: %.2f, %s, word: %s\n", avgFreq, result, word);
        }
    }
}
