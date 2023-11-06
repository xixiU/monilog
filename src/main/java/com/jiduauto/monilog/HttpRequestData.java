package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.*;

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
    private String originUrl;

    static HttpRequestData of1(String requestUrl, String bodyParams, Map<String, String[]> parameters, Map<String, String> headers) {
        Map<String, Collection<String>> queries = new HashMap<>();
        if (MapUtils.isNotEmpty(parameters)) {
            for (Map.Entry<String, String[]> me : parameters.entrySet()) {
                queries.put(me.getKey(), Arrays.asList(me.getValue()));
            }
        }
        return of3(requestUrl, bodyParams, queries, headers);
    }

    static HttpRequestData of2(String requestUrl, String bodyParams, Map<String, Collection<String>> queries, Map<String, Collection<String>> headers) {
        Map<String, String> headerMap = new HashMap<>();
        if (MapUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, Collection<String>> me : headers.entrySet()) {
                if (StringUtils.isNotBlank(me.getKey()) && CollectionUtils.isNotEmpty(me.getValue())) {
                    headerMap.put(me.getKey(), String.join(",", me.getValue()));
                }
            }
        }
        return of3(requestUrl, bodyParams, queries, headerMap);
    }

    /**
     *
     * @param requestUrl 请求地址
     * @param bodyParams body参数
     * @param queries query参数
     * @param headers    header信息
     * @return
     */
    static HttpRequestData of3(String requestUrl, String bodyParams, Map<String, Collection<String>> queries, Map<String, String> headers) {
        HttpRequestData data = new HttpRequestData();
        data.originUrl = requestUrl;
        if (StringUtils.isNotBlank(bodyParams)) {
            JSON json = StringUtil.tryConvert2Json(bodyParams);
            data.body = json == null ? bodyParams : json;
        }
        Map<String, Collection<String>> queryMap = mergeMaps(queries, StringUtil.getQueryMap(requestUrl));
        if (MapUtils.isNotEmpty(queryMap)) {
            // 将请求requestUrl路径?后的参数也拼接到queries中
            data.query = StringUtil.encodeQueryString(queryMap);
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
        if (StringUtils.isNotBlank(originUrl)) {
            obj.put("originUrl", originUrl);
        }
        return obj;
    }

    public static Map<String, Collection<String>> mergeMaps(Map<String, Collection<String>> map1, Map<String, Collection<String>> map2) {
        Map<String, Collection<String>> mergedMap = new HashMap<>();
        if (map1 == null) {
            return map2;
        }
        if (map2 == null) {
            return map1;
        }
        // 遍历第一个Map的键值对，将键和值添加到结果Map中
        for (Map.Entry<String, Collection<String>> entry : map1.entrySet()) {
            String key = entry.getKey();
            Collection<String> values = entry.getValue();
            mergedMap.put(key, new HashSet<>(values));
        }

        // 遍历第二个Map的键值对，合并到结果Map中
        for (Map.Entry<String, Collection<String>> entry : map2.entrySet()) {
            String key = entry.getKey();
            Collection<String> values = entry.getValue();

            if (mergedMap.containsKey(key)) {
                // 如果结果Map中已存在该键，则将值合并，并去重
                Collection<String> existingValues = mergedMap.get(key);
                existingValues.addAll(values);
            } else {
                // 如果结果Map中不存在该键，则直接添加键值对
                mergedMap.put(key, new HashSet<>(values));
            }
        }

        return mergedMap;
    }


    /**
     * 提取路径，如http://baidu.com/test?a=1 返回/test,此处针对restful分格的接口也会存在问题，如/getOrder/123
     * @param url url
     * @return 仅路径信息不包含参数与host
     */
    public static String extractPath(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        try{
            URL urlResource = new URL(url);
            return urlResource.getPath();
        }catch (Exception e){
            if (StringUtils.isBlank(url) || !url.contains("?")) {
                return url;
            }
            String[] uriAndParams = url.split("\\?");
            return uriAndParams[0];
        }
    }
}

