package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yp
 * @date 2023/08/07
 */
@Getter
@Setter
class HttpRequestData {
    private Object body;
    private String query;
    private Map<String, String> headers;

    static HttpRequestData of1(String bodyParams, Map<String, String[]> parameters, Map<String, String> headers) {
        Map<String, Collection<String>> queries = new HashMap<>();
        if (MapUtils.isNotEmpty(parameters)) {
            for (Map.Entry<String, String[]> me : parameters.entrySet()) {
                queries.put(me.getKey(), Arrays.asList(me.getValue()));
            }
        }
        return of3(bodyParams, queries, headers);
    }

    static HttpRequestData of2(String bodyParams, Map<String, Collection<String>> queries, Map<String, Collection<String>> headers) {
        Map<String, String> headerMap = new HashMap<>();
        if (MapUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, Collection<String>> me : headers.entrySet()) {
                if (StringUtils.isNotBlank(me.getKey()) && CollectionUtils.isNotEmpty(me.getValue())) {
                    headerMap.put(me.getKey(), String.join(",", me.getValue()));
                }
            }
        }
        return of3(bodyParams, queries, headerMap);
    }

    static HttpRequestData of3(String bodyParams, Map<String, Collection<String>> queries, Map<String, String> headers) {
        HttpRequestData data = new HttpRequestData();
        if (StringUtils.isNotBlank(bodyParams)) {
            JSON json = StringUtil.tryConvert2Json(bodyParams);
            data.body = json == null ? bodyParams : json;
        }
        if (MapUtils.isNotEmpty(queries)) {
            data.query = StringUtil.encodeQueryString(queries);
        }
        if (MapUtils.isNotEmpty(headers)) {
            Map<String, String> headerMap = new HashMap<>();
            for (Map.Entry<String, String> me : headers.entrySet()) {
                if (StringUtils.isNotBlank(me.getKey()) && me.getValue() != null) {
                    headerMap.put(me.getKey(), me.getValue());
                }
            }
            data.headers = headerMap;
        }
        return data;
    }

    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        if (body != null) {
            obj.put("body", body);
        }
        if (StringUtils.isNotBlank(query)) {
            obj.put("query", query);
        }
        if (MapUtils.isNotEmpty(headers)) {
            obj.put("headers", headers);
        }
        return obj;
    }
}
