package com.jiduauto.log.weblogspringbootstarter.util;

import java.util.List;

public class UrlMatcherUtils {

    public static boolean checkContains(List<String> blackUrlList, String url) {
        for (String blackUrl : blackUrlList) {
            if (matchUrl(blackUrl, url)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchUrl(String pattern, String url) {
        String[] patternParts = pattern.split("/");
        String[] urlParts = url.split("/");

        int patternLength = patternParts.length;
        int urlLength = urlParts.length;

        if (patternLength > urlLength) {
            return false;
        }

        for (int i = 0; i < patternLength; i++) {
            String patternPart = patternParts[i];
            String urlPart = urlParts[i];

            if (!"*".equals(patternPart) && !patternPart.equals(urlPart)) {
                return false;
            }
        }

        return true;
    }
}