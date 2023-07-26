package com.jiduauto.log.grpc;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * @author yp
 * @date 2023/07/25
 */
@Slf4j
abstract class InterceptorHelper {
    protected static final String TIME_KEY = "nowTime";

    protected static long parseCostTime(Map<String, Object> context) {
        Long nowTime = (Long) context.get(TIME_KEY);
        long cost = 0;
        if (nowTime != null) {
            cost = System.currentTimeMillis() - nowTime;
        }
        return cost;
    }

    protected static String print2Json(MessageOrBuilder message) {
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(message);
        } catch (Exception e) {
            log.error("rpc message序列化成json错误", e);
            return message.toString();
        }
    }

    protected static Object tryConvert2Json(MessageOrBuilder message) {
        try {
            String json = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
            try {
                return JSON.parse(json);
            } catch (Exception ignore) {
                return json;
            }
        } catch (Exception e) {
            log.error("rpc message序列化成json错误", e);
            return message.toString();
        }
    }

    protected static String buildActionName(String fullMethodName, String serviceName) {
        return StringUtils.remove(StringUtils.removeStart(fullMethodName, serviceName), "/");
    }

    protected static Class<?> getCurrentProtoClass(MethodDescriptor<?, ?> method) {
        Class<?> cls = null;
        if (method.getSchemaDescriptor() != null) {
            cls = method.getSchemaDescriptor().getClass();
        }
        if (cls != null && cls.isMemberClass()) {
            cls = cls.getDeclaringClass();
        }
        return cls;
    }
}
