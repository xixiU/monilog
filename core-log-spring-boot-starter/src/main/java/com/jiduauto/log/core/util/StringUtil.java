package com.jiduauto.log.core.util;

import com.alibaba.fastjson.JSON;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.Map;

/**
 * @author yp
 * @date 2023/07/25
 */
public class StringUtil {

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

    public static String encodeQueryString(Map<String, Collection<String>> params) {
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, Collection<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            Collection<String> values = entry.getValue();
            if (org.apache.commons.lang3.StringUtils.isBlank(key) || CollectionUtils.isEmpty(values)) {
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
