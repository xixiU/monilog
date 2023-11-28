package com.jiduauto.monilog;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

class HttpUtil {
    private static final Set<String> TEXT_TYPES = Sets.newHashSet("application/json", "application/xml", "application/xhtml+xml", "text/");

    private static final Set<String> STREAMING_TYPES = Sets.newHashSet("application/octet-stream", "application/pdf", "application/zip", "application/x-", "application/vnd.", "application/ms", "image/", "audio/", "video/");

    static Boolean checkContentTypeIsStream(String contentType){
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
}
