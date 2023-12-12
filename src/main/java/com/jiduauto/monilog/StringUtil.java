package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONValidator;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yp
 * @date 2023/07/25
 */
@Slf4j
class StringUtil {
    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();
    private static final AntPathMatcher ANT_CLASS_MATCHER = new AntPathMatcher(".");

    public static boolean checkClassMatch(Collection<String> classList, String toCheck) {
        return checkMatch(classList, toCheck, ANT_CLASS_MATCHER);
    }

    public static boolean checkContainsIgnoreCase(Collection<String> sourceList, String toCheck) {
        if (CollectionUtils.isEmpty(sourceList) || StringUtils.isBlank(toCheck)) {
            return false;
        }
        for (String item : sourceList) {
            if (toCheck.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkPathMatch(Collection<String> pathList, String toCheckPath) {
        return checkMatch(pathList, toCheckPath, ANT_PATH_MATCHER);
    }

    public static boolean checkListItemContains(Collection<String> keyWords, String message) {
        if (CollectionUtils.isEmpty(keyWords) || StringUtils.isBlank(message)) {
            return false;
        }
        for (String item : keyWords) {
            if (message.contains(item)) {
                return true;
            }
        }
        return false;
    }


    private static boolean checkMatch(Collection<String> targets, String toCheck, AntPathMatcher matcher) {
        if (CollectionUtils.isEmpty(targets) || StringUtils.isBlank(toCheck)) {
            return false;
        }
        for (String pattern : targets) {
            if (matcher.match(pattern, toCheck)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从注解中获取tag列表
     */
    public static List<String> getTagList(MoniLogTags logTags) {
        if (logTags == null || logTags.value() == null || logTags.value().length == 0) {
            return new ArrayList<>();
        }
        if (logTags.value().length % 2 == 0) {
            return Arrays.stream(logTags.value()).map(String::trim).collect(Collectors.toList());
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
        if (StringUtils.isBlank(str)) {
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
        try {
            Map<String, String> jsonMap = StringUtil.tryConvert2Map(strMap);
            if (MapUtils.isEmpty(jsonMap) || oriTags == null || oriTags.length == 0) {
                return oriTags;
            }
            return processUserTag(jsonMap, oriTags);
        } catch (Exception e) {
            MoniLogUtil.innerDebug("processUserTag process error", e);
        }
        return null;
    }

    public static String encodeQueryString(Map<String, Collection<String>> params) {
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, Collection<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            Collection<String> values = entry.getValue();
            if (StringUtils.isBlank(key)) {
                continue;
            }
            if (queryString.length() > 0) {
                queryString.append("&");
            }
            if (CollectionUtils.isEmpty(values)) {
                queryString.append(key).append("=");
                continue;
            }

            queryString.append(key)
                    .append("=")
                    .append(String.join(",", values));
        }
        return queryString.toString();
    }

    /**
     * 从url中提取query参数
     */
    public static Map<String, Collection<String>> getQueryMap(String url) {
        if (StringUtils.isBlank(url) || !url.contains("?")) {
            return null;
        }
        String query = url.substring(url.indexOf("?") + 1);
        if (StringUtils.isBlank(query)) {
            return null;
        }
        Map<String, Collection<String>> queryParams = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx < 0) {
                // 只有key没有value,如a/b?c
                Collection<String> valueList = queryParams.computeIfAbsent(pair, item -> new ArrayList<>());
                valueList.add("");
                continue;
            }
            String key = pair.substring(0, idx);
            String value = pair.substring(idx + 1);
            Collection<String> valueList = queryParams.computeIfAbsent(key, item -> new ArrayList<>());
            valueList.add(value);
        }
        return queryParams;
    }

    static String encodeByteArray(byte[] body, Charset charset, String defaultStr) {
        // 任意一个为空则认为是二进制的
        if (body == null || charset == null) {
            return defaultStr;
        }
        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException ex) {
            // 无法解码认为是二进制的
            return defaultStr;
        }
    }
    static boolean isBinaryArray(byte[] body, Charset charset) {
        // 任意一个为空则认为是二进制的
        if (body == null || charset == null) {
            return true;
        }
        try {
            charset.newDecoder().decode(ByteBuffer.wrap(body));
            return false;
        } catch (CharacterCodingException ex) {
            // 无法解码认为是二进制的
            return true;
        }
    }
}
