package com.jiduauto.log.feign;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jiduauto.log.core.ErrorInfo;
import com.jiduauto.log.core.LogParser;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.parse.ParsedResult;
import com.jiduauto.log.core.parse.ResultParseStrategy;
import com.jiduauto.log.core.util.*;
import feign.Client;
import feign.MethodMetadata;
import feign.Request;
import feign.Response;
import lombok.SneakyThrows;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yp
 * @date 2023/07/25
 */
class EnhancedFeignClient implements Client {
    private final Client realClient;
    private final String defaultBoolExpr;

    public EnhancedFeignClient(Client realClient, String defaultBoolExpr) {
        this.realClient = realClient;
        this.defaultBoolExpr = defaultBoolExpr;
    }

    @SneakyThrows
    @Override
    public Response execute(Request request, Request.Options options) {
        long start = System.currentTimeMillis();
        Response originResponse = null;
        Throwable ex = null;
        long cost = 0;
        try {
            //原始调用
            originResponse = realClient.execute(request, options);
        } catch (Throwable e) {
            ex = e;
        } finally {
            cost = System.currentTimeMillis() - start;
        }
        MethodMetadata mm = request.requestTemplate().methodMetadata();
        Method m = mm.method();
        MonitorLogParams mlp = new MonitorLogParams();
        mlp.setServiceCls(m.getDeclaringClass());
        mlp.setService(m.getDeclaringClass().getSimpleName());
        mlp.setAction(m.getName());
        mlp.setTags(new String[]{"method", request.httpMethod().toString(), "url", request.url()});

        mlp.setCost(cost);
        mlp.setException(ex);
        mlp.setSuccess(ex == null);
        mlp.setLogPoint(LogPoint.REMOTE_CLIENT);
        mlp.setInput(new Object[]{formatRequestInfo(request)});
        mlp.setMsgCode(ErrorEnum.SUCCESS.name());
        mlp.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
        if (ex != null) {
            ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
            if (errorInfo != null) {
                mlp.setMsgCode(errorInfo.getErrorCode());
                mlp.setMsgInfo(errorInfo.getErrorMsg());
            }
            throw ex;
        }
        //包装响应
        Charset charset = request.charset();
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        Response ret;
        try {
            BufferingFeignClientResponse response = new BufferingFeignClientResponse(originResponse);
            mlp.setSuccess(mlp.isSuccess() && response.status() == HttpStatus.OK.value());
            if (!mlp.isSuccess()) {
                mlp.setMsgCode(String.valueOf(response.status()));
                mlp.setMsgInfo(ErrorEnum.FAILED.getMsg());
            }
            String resultStr = null;
            if (response.isDownstream()) {
                mlp.setOutput("Binary data");
            } else {
                resultStr = response.body(); //读掉原始response中的数据
                mlp.setOutput(resultStr);
            }
            if (resultStr != null && response.isJson()) {
                Object json = JSON.parse(resultStr);
                if (json != null) {
                    mlp.setOutput(json);
                    LogParser cl = ReflectUtil.getAnnotation(LogParser.class, mlp.getServiceCls(), m);
                    //尝试更精确的提取业务失败信息
                    ResultParseStrategy rps = cl == null ? null : cl.resultParseStrategy();//默认使用IfSuccess策略
                    String boolExpr = cl == null ? StringUtils.trimToNull(defaultBoolExpr) : cl.boolExpr();
                    String codeExpr = cl == null ? null : cl.errorCodeExpr();
                    String msgExpr = cl == null ? null : cl.errorMsgExpr();
                    ParsedResult parsedResult = ResultParseUtil.parseResult(json, rps, null, boolExpr, codeExpr, msgExpr);
                    mlp.setSuccess(parsedResult.isSuccess());
                    mlp.setMsgCode(parsedResult.getMsgCode());
                    mlp.setMsgInfo(parsedResult.getMsgInfo());
                }
            }
            if (resultStr != null) {
                //重写将数据写入原始response中去
                ret = response.getResponse().toBuilder().body(resultStr, charset).build();
            } else {
                ret = response.getResponse();
            }
            response.close();
        } catch (Exception e) {
            return originResponse;
        } finally {
            MonitorLogUtil.log(mlp);
        }
        return ret;
    }

    private static JSONObject formatRequestInfo(Request request) {
        String bodyParams = request.isBinary() ? "Binary data" : request.length() == 0 ? null : new String(request.body(), request.charset()).trim();
        Map<String, Collection<String>> queries = request.requestTemplate().queries();
        Map<String, Collection<String>> headers = request.headers();
        JSONObject obj = new JSONObject();
        if (StringUtils.isNotBlank(bodyParams)) {
            JSON json = StringUtil.tryConvert2Json(bodyParams);
            obj.put("body", json != null ? json : bodyParams);
        }
        if (MapUtils.isNotEmpty(queries)) {
            obj.put("query", StringUtil.encodeQueryString(queries));
        }
        if (MapUtils.isNotEmpty(headers)) {
            Map<String, String> headerMap = new HashMap<>();
            for (Map.Entry<String, Collection<String>> me : headers.entrySet()) {
                headerMap.put(me.getKey(), String.join(",", me.getValue()));
            }
            obj.put("headers", headerMap);
        }
        return obj;
    }
}
