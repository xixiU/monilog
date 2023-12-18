package com.jiduauto.monilog;

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
            if (StringUtil.isRandomNum(segments[i]) || StringUtil.isRandomStr(segments[i])) {
                segments[i] = "{xxx}";
            }
        }
        return StringUtils.join(segments, PATH_SEP);
    }

    public static void main(String[] args) {
        boolean b50 = RandomStringDetector.isRandomWord("abcdkljlkalksdjfkls");
        boolean b1 = RandomStringDetector.isRandomWord("alkjsdflkjsd");
        boolean b2 = RandomStringDetector.isRandomWord("queryEmployeeInfo");
        boolean b3 = RandomStringDetector.isRandomWord("hello123");
        boolean b4 = RandomStringDetector.isRandomWord("abcdKLljlk20023");
        boolean b51 = RandomStringDetector.isRandomWord("abcdkljlkalksdjfkls");
        boolean b6 = RandomStringDetector.isRandomWord("httpClient");
        System.out.println(b6);

        String s1 = extractPathWithoutPathParams("http://baidu.com");
        String s2 = extractPathWithoutPathParams("http://baidu.com/");
        String s3 = extractPathWithoutPathParams("http://baidu.com/abc/de");
        String s4 = extractPathWithoutPathParams("http://baidu.com/a/b/c/?t=23");
        String s5 = extractPathWithoutPathParams("baidu.com/a=23&b=3");
        String s6 = extractPathWithoutPathParams("http:/baidu.com?t=434");
        String s7 = extractPathWithoutPathParams("/ab/c/d");
        String s8 = extractPathWithoutPathParams("/a/b/c/d?t=23");
        String s9 = extractPathWithoutPathParams("/a/b/230982432/d?t=23");
        String s10 = extractPathWithoutPathParams("/a/b/bajklsdjfksljk2l3/d?t=23");
        String s11 = extractPathWithoutPathParams("/a/b/bajklsdjfksljk/d?t=23");

        System.out.println(s8);
    }
}
