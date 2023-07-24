package com.jiduauto.log.weblogspringbootstarter.util;

import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;

public class UrlMatcherUtils {

    public static boolean checkUrlMatch(List<String> urls, String url) {
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        for (String pattern : urls) {
            if (antPathMatcher.match(pattern, url)) {
                return true;
            }
        }
        return false;
    }


    public static void main(String[] args) {
        List<String> urlPatterns = Arrays.asList("/api/v1/users/*", "/api/v1/posts/*");
        String url1 = "/api/v1/users/123";
        String url2 = "/api/v1/comments/456";
        boolean match1 = checkUrlMatch(urlPatterns, url1);
        boolean match2 = checkUrlMatch(urlPatterns, url2);
        System.out.println("URL1 matching: " + match1);
        System.out.println("URL2 matching: " + match2);
    }
}