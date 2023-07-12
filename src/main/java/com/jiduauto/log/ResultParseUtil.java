package com.jiduauto.log;


import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * @author yepei
 */
public final class ResultParseUtil {
    public static ParsedResult parseResult(Object returnObj, ResultParseStrategy strategy, Throwable t, String boolExpr, String codeExpr, String msgExpr) {
        boolean noStrategy = strategy == null;
        Boolean parsedSucc = null;
        if (noStrategy) {//默认使用IfSuccess策略
            strategy = ResultParseStrategy.IfSuccess;
        }
        if (strategy == ResultParseStrategy.IfSuccess) {
            //如果是IfSuccess策略，则提前计算结果成败
            parsedSucc = ResultParser.parseBoolean(returnObj, boolExpr);
            if (noStrategy && parsedSucc == null) {
                //如果未指定判定策略，且默认的策略又计算不出结果，则将策略置为非异常
                strategy = ResultParseStrategy.IfNotException;
            }
        }

        if (t != null) {//有异常
            ErrorInfo errorInfo = ExceptionUtil.parseException(t);
            return new ParsedResult(false, errorInfo.getErrorCode(), errorInfo.getErrorMsg());
        }

        boolean succ;
        String successCode = "SUCCESS";
        String successMsg = "成功";
        String msgCode;
        String msgInfo;
        switch (strategy) {
            case IfNotNull:
                succ = returnObj != null;
                msgCode = succ ? successCode : "NULL_RESULT";
                msgInfo = succ ? successMsg : "结果为null";
                return new ParsedResult(succ, msgCode, msgInfo);
            case IfNotEmpty:
                succ = isNotEmpty(returnObj);
                msgCode = succ ? successCode : "EMPTY_RESULT";
                msgInfo = succ ? successMsg : "结果为null";
                return new ParsedResult(succ, msgCode, msgInfo);
            case IfSuccess:
                msgCode = ResultParser.parseErrCode(returnObj, codeExpr);
                msgInfo = ResultParser.parseErrMsg(returnObj, msgExpr);
                return new ParsedResult(parsedSucc != null && parsedSucc, msgCode, msgInfo);
            case IfNotException:
            default:
                succ = isNotEmpty(returnObj);
                msgCode = succ ? successCode : "EMPTY_RESULT";
                msgInfo = succ ? successMsg : "结果为空";
                return new ParsedResult(succ, msgCode, msgInfo);
        }
    }


    public ParsedResult parseResult(Object returnObj, ResultParseStrategy strategy, Throwable t) {
        return parseResult(returnObj, strategy, t, null, null, null);
    }

    private static boolean isNotEmpty(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof CharSequence) {
            return !StringUtils.isBlank((CharSequence) o);
        }
        return (!(o instanceof Map) || ((Map<?, ?>) o).size() != 0)
                && (!o.getClass().isArray() || Array.getLength(o) != 0)
                && (!(o instanceof Collection) || ((Collection<?>) o).size() != 0);
    }
}
