package com.jiduauto.monitor.log;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yepei
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LogParser {
    /**
     * 服务名称，默认会取所在类的简单类名
     */
    String serviceName() default "";

    /**
     * 结果解析策略, 默认为IfSuccess， 其它选项：IfNotNull、IfNotEmpty、IfNotException
     * 注意，如果未指定判定策略，且默认的IfSuccess策略又计算不出结果，此时会自动使用IfNotException策略
     */
    ResultParseStrategy resultParseStrategy() default ResultParseStrategy.IfSuccess;

    /**
     * 自定义结果判定规则表达式， 默认值为："$.success,$.succeeded,$.succeed,$.succ,$.code=SUCCESS,$.isOk(),$.isSuccess(),$.getSuccess(),"
     * + "$.isSucceed(),$.getSucceed(),$.isSucceeded(),$.getSucceeded(),$.isSucc(),$.getResult()"
     */
    String boolExpr() default ResultParser.Default_Bool_Expr;

    /**
     * 自定义错误码判断表达式，默认值为：$.msgCode,$.resultCode,$.errorCode,$.responseCode,$.retCode,$.code," +
     * "$.getMsgCode(),$.getResultCode(),$.getErrorCode(),$.getResponseCode(),$.getRetCode(),$.getCode()
     */
    String errorCodeExpr() default ResultParser.Default_ErrCode_Expr;

    /**
     * 自定义错误原因表达式，默认值为："$.msgInfo,$.message,$.msg,$.resultMsg,$.errorMsg,$.errMsg,$.responseMsg,$.responseMessage,$.retMsg,$.subResultCode,$.errorDesc" +
     * "$.getMsgInfo(),$.getMessage(),$.getMsg(),$.getResultMsg(),$.getErrorMsg(),$.getResponseMsg(),$.getRetMsg()"
     */
    String errorMsgExpr() default ResultParser.Default_ErrMsg_Expr;

    /**
     * 用于手动构建注解实例，通常用作参数传递
     */
    @LogParser
    interface Default {
        static LogParser buildInstance() {
            return buildInstance(null, null, null);
        }

        static LogParser buildInstance(String boolExpr) {
            return buildInstance(boolExpr, null, null);
        }

        static LogParser buildInstance(String boolExpr, String errorCodeExpr, String errorMsgExpr) {
            return buildInstance(null, boolExpr, errorCodeExpr, errorMsgExpr);
        }

        static LogParser buildInstance(ResultParseStrategy resultParseStrategy, String boolExpr, String errorCodeExpr, String errorMsgExpr) {
            return buildInstance(null, resultParseStrategy, boolExpr, errorCodeExpr, errorMsgExpr);
        }

        static LogParser buildInstance(String serviceName, ResultParseStrategy resultParseStrategy, String boolExpr, String errorCodeExpr, String errorMsgExpr) {
            LogParser origin = Default.class.getAnnotation(LogParser.class);
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("serviceName", serviceName);
            newValues.put("resultParseStrategy", resultParseStrategy);
            newValues.put("boolExpr", boolExpr);
            newValues.put("errorCodeExpr", errorCodeExpr);
            newValues.put("errorMsgExpr", errorMsgExpr);
            return ProxyUtils.copyAnnotation(origin, newValues);
        }
    }

}
