package com.jiduauto.log.util;


import com.jiduauto.log.*;
import com.jiduauto.log.enums.ErrorEnum;
import com.jiduauto.log.parse.ParsedResult;
import com.jiduauto.log.parse.ResultParseStrategy;
import com.jiduauto.log.parse.ResultParser;
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

        boolean succ = true;
        ErrorEnum errorEnum = ErrorEnum.SUCCESS;
        switch (strategy) {
            case IfSuccess:
                String msgCode = ResultParser.parseErrCode(returnObj, codeExpr);
                String msgInfo = ResultParser.parseErrMsg(returnObj, msgExpr);
                return new ParsedResult(parsedSucc != null && parsedSucc, msgCode, msgInfo);
            case IfNotException:
                return new ParsedResult(succ, errorEnum.name(), errorEnum.getMsg());
            case IfNotNull:
                if (!(succ = returnObj != null)) {
                    errorEnum = ErrorEnum.NULL_RESULT;
                }
                return new ParsedResult(succ, errorEnum.name(), errorEnum.getMsg());
            case IfNotEmpty:
            default:
                if (!(succ = isNotEmpty(returnObj))) {
                    errorEnum = ErrorEnum.EMPTY_RESULT;
                }
                return new ParsedResult(succ, errorEnum.name(), errorEnum.getMsg());
        }
    }


    public static ParsedResult parseResult(Object returnObj, ResultParseStrategy strategy, Throwable t) {
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
