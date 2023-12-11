package com.jiduauto.monilog;

import com.google.common.collect.Sets;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.Set;

class HttpUtil {
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
        return StringUtils.containsIgnoreCase(header, "attachment")
                || StringUtils.containsIgnoreCase(header, "filename");
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
}
