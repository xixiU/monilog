package com.jiduauto.monilog;

import com.google.common.collect.Sets;

import java.util.Set;

class HttpUtil {
    private static final Set<String> TEXT_TYPES = Sets.newHashSet("application/json", "application/xml", "application/xhtml+xml", "text/");

    private static final Set<String> STREAMING_TYPES = Sets.newHashSet("application/octet-stream", "application/pdf", "application/zip", "application/x-", "image/", "audio/", "video/");

    static boolean checkContentTypeIsStream(String contentType){
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
        return false;

    }
}
