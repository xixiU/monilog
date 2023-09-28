package com.jiduauto.monilog;


import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * @author yepei
 */
final class ResultParseUtil {
    static ParsedResult parseResult(Object returnObj, ResultParseStrategy strategy, Throwable t, String boolExpr, String codeExpr, String msgExpr) {
        if (t != null) {//有异常
            ErrorInfo errorInfo = ExceptionUtil.parseException(t);
            return new ParsedResult(false, errorInfo.getErrorCode(), errorInfo.getErrorMsg());
        }
        boolExpr = correctBoolExpr(boolExpr);
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

        boolean succ = true;
        ErrorEnum errorEnum = ErrorEnum.SUCCESS;
        String msgCode = ResultParser.parseErrCode(returnObj, codeExpr);
        String msgInfo = ResultParser.parseErrMsg(returnObj, msgExpr);
        switch (strategy) {
            case IfSuccess:
                //此处属于悲观判定：只有当解析成功且值为true，才认为成功。 解析结果未知也认为是失败，可以倒逼使用的地方主动纠正判定逻辑，提高告警的敏感性
                if (!(succ = parsedSucc != null && parsedSucc)) {
                    errorEnum = ErrorEnum.FAILED;
                }
                break;
            case IfNotException:
                break;
            case IfNotNull:
                if (!(succ = returnObj != null)) {
                    errorEnum = ErrorEnum.NULL_RESULT;
                }
                break;
            case IfNotEmpty:
            default:
                if (!(succ = isNotEmpty(returnObj))) {
                    errorEnum = ErrorEnum.EMPTY_RESULT;
                }
                break;
        }
        if (StringUtils.isBlank(msgCode)) {
            msgCode = errorEnum.name();
        }
        if (StringUtils.isBlank(msgInfo)) {
            msgInfo = errorEnum.getMsg();
        }
        return new ParsedResult(succ, msgCode, msgInfo);
    }

    private static String correctBoolExpr(String boolExpr) {
        if (StringUtils.isBlank(boolExpr)) {
            try {
                MoniLogProperties moniLogProperties = SpringUtils.getBeanWithoutException(MoniLogProperties.class);
                boolExpr = moniLogProperties == null ? null : moniLogProperties.getGlobalDefaultBoolExpr();
            } catch (Exception ex) {
                MoniLogUtil.innerDebug("getBean of MoniLogProperties error", ex);
            }
        }
        if (StringUtils.isNotBlank(boolExpr)) {
            boolExpr = StringUtils.strip(boolExpr, ",");
            if (StringUtils.startsWith(boolExpr, "+")) {
                boolExpr = boolExpr.substring(1) + "," + ResultParser.Default_Bool_Expr;
            }
        }
        return boolExpr;
    }


    static ParsedResult parseResult(Object returnObj, ResultParseStrategy strategy, Throwable t) {
        return parseResult(returnObj, strategy, t, null, null, null);
    }

    private static boolean isNotEmpty(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof CharSequence) {
            return !StringUtils.isBlank((CharSequence) o);
        }
        return (!(o instanceof Map) || !((Map<?, ?>) o).isEmpty())
                && (!o.getClass().isArray() || Array.getLength(o) != 0)
                && (!(o instanceof Collection) || !((Collection<?>) o).isEmpty());
    }

    /**
     * 根据解析策略解析解析的结果，提取更加精确的业务信息
     */
    static void parseResultAndSet(LogParser cl, JSON resultJson, MoniLogParams logParams) {
        //默认使用IfSuccess策略
        ResultParseStrategy rps = cl == null ? null : cl.resultParseStrategy();
        String boolExpr = cl == null ? null : cl.boolExpr();
        String codeExpr = cl == null ? null : cl.errorCodeExpr();
        String msgExpr = cl == null ? null : cl.errorMsgExpr();
        ParsedResult pr = ResultParseUtil.parseResult(resultJson, rps, null, boolExpr, codeExpr, msgExpr);
        logParams.setSuccess(pr.isSuccess());
        logParams.setMsgCode(pr.getMsgCode());
        logParams.setMsgInfo(pr.getMsgInfo());
    }
}
