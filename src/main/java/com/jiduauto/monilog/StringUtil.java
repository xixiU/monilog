package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONValidator;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yp
 * @date 2023/07/25
 */
@Slf4j
class StringUtil {
    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public static boolean checkPathMatch(Collection<String> pathList, String toCheckPath) {
        if (CollectionUtils.isEmpty(pathList)) {
            return false;
        }
        for (String pattern : pathList) {
            if (antPathMatcher.match(pattern, toCheckPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从注解中获取tag列表
     *
     * @param logTags
     * @return
     */
    public static List<String> getTagList(MoniLogTags logTags) {
        if (logTags == null || logTags.tags() == null || logTags.tags().length == 0) {
            return new ArrayList<>();
        }
        if (logTags.tags().length % 2 == 0) {
            return Arrays.stream(logTags.tags()).map(String::trim).collect(Collectors.toList());
        } else {
            // prometheus tag是key,value结构，非偶数tag prometheus上报会报错，这里只打一行日志提醒
            log.error("tags length must be double，tags：{}", JSON.toJSONString(logTags));
        }
        return new ArrayList<>();
    }

    public static String[] getTagArray(MoniLogTags logTags) {
        List<String> tagList = getTagList(logTags);
        if (CollectionUtils.isEmpty(tagList)) {
            return null;
        }
        return tagList.toArray(new String[0]);
    }

    /**
     * 尝试转换成json，转换不了异常吞掉
     */
    public static JSON tryConvert2Json(String str) {
        if (str == null) {
            return null;
        }
        if (str.isEmpty()) {
            return null;
        }
        boolean maybeObj = str.startsWith("{") && str.endsWith("}");
        boolean maybeArr = str.startsWith("[") && str.endsWith("]");
        if (!maybeObj && !maybeArr) {
            return null;
        }
        try {
            return maybeObj ? JSON.parseObject(str) : JSON.parseArray(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 尝试转换成jsonObject，转换不了异常吞掉
     */
    public static Map<String, String> tryConvert2Map(String str) {
        if (str == null || str.isEmpty()) {
            return new HashMap<>();
        }

        try {
            boolean validate = JSONValidator.from(str).validate();
            if (!validate) {
                return new HashMap<>();
            }
            return JSON.parseObject(str, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public static String[] processUserTag(Map<String, String> jsonMap, String[] oriTags) {
        if (MapUtils.isEmpty(jsonMap) || oriTags == null || oriTags.length == 0) {
            return oriTags;
        }
        String[] replaceTags = Arrays.copyOf(oriTags, oriTags.length);
        for (int i = 0; i < replaceTags.length; i++) {
            if ((!replaceTags[i].startsWith("${") && !replaceTags[i].startsWith("{")) || !replaceTags[i].endsWith("}")) {
                continue;
            }
            int startIndex = 1;
            if (replaceTags[i].startsWith("${")) {
                startIndex = 2;
            }
            String parameterName = replaceTags[i].substring(startIndex, replaceTags[i].length() - 1);
            String resultTagValue = jsonMap.get(parameterName);
            replaceTags[i] = StringUtils.isNotBlank(resultTagValue) ? resultTagValue : "00";
        }
        return replaceTags;
    }

    public static String[] processUserTag(String strMap, String[] oriTags) {
        Map<String, String> jsonMap = StringUtil.tryConvert2Map(strMap);
        if (MapUtils.isEmpty(jsonMap) || oriTags == null || oriTags.length == 0) {
            return oriTags;
        }
        return processUserTag(jsonMap, oriTags);
    }

    public static String encodeQueryString(Map<String, Collection<String>> params) {
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, Collection<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            Collection<String> values = entry.getValue();
            if (StringUtils.isBlank(key) || CollectionUtils.isEmpty(values)) {
                continue;
            }
            if (queryString.length() > 0) {
                queryString.append("&");
            }
            queryString.append(key)
                    .append("=")
                    .append(String.join(",", values));
        }
        return queryString.toString();
    }

    public static String encodeQueryStrings(Map<String, String[]> params) {
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (StringUtils.isBlank(key) || values == null || values.length == 0) {
                continue;
            }
            if (queryString.length() > 0) {
                queryString.append("&");
            }
            queryString.append(key)
                    .append("=")
                    .append(String.join(",", values));
        }
        return queryString.toString();
    }
}
