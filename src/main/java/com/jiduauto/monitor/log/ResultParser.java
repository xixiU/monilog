package com.jiduauto.monitor.log;

import com.alibaba.fastjson.JSONPath;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yepei
 */
@Slf4j
final class ResultParser {
    private static final String EXPECT_SPLITTER1 = "==";
    private static final String EXPECT_SPLITTER2 = "=";

    /**
     * 默认的结果解析路径，注意对于基于方法的解析方式，仅支持无参方法
     */
    public static final String Default_Bool_Expr = "$.success,$.succeeded,$.succeed,$.succ,$.code=SUCCESS,$.isOk(),$.isSuccess(),$.getSuccess(),$.isSucceed(),$.getSucceed(),$.isSucceeded(),$.getSucceeded(),$.isSucc(),$.getResult()";
    /**
     * 默认的错误码解析路径，注意对于基于方法的解析方式，仅支持无参方法
     */
    public static final String Default_ErrCode_Expr = "$.msgCode,$.resultCode,$.errorCode,$.responseCode,$.retCode,$.code,$.status," +
            "$.getMsgCode(),$.getResultCode(),$.getErrorCode(),$.getResponseCode(),$.getRetCode(),$.getCode()";
    /**
     * 默认的错误原因解析路径，注意对于基于方法的解析方式，仅支持无参方法
     */
    public static final String Default_ErrMsg_Expr = "$.msgInfo,$.message,$.msg,$.resultMsg,$.errorMsg,$.errMsg,$.responseMsg,$.responseMessage,$.retMsg,$.subResultCode,$.errorDesc,$.error," +
            "$.getMsgInfo(),$.getMessage(),$.getMsg(),$.getResultMsg(),$.getErrorMsg(),$.getResponseMsg(),$.getRetMsg()";

    public static Integer parseIntCode(Object obj) {
        return parseIntCode(obj, null);
    }

    public static String parseErrCode(Object obj) {
        return parseErrCode(obj, null);
    }

    public static String parseErrMsg(Object obj) {
        return parseErrMsg(obj, null);
    }

    public static Boolean parseBoolean(Object obj) {
        return parseBoolean(obj, null);
    }

    public static boolean parseBoolVal(Object obj, boolean defaultVal) {
        return parseBoolVal(obj, null, defaultVal);
    }

    public static String parseErrCode(Object obj, @Nullable String jsonpaths) {
        if (obj instanceof Throwable) {
            return ExceptionUtil.parseException((Throwable) obj).getErrorCode();
        }
        if (StringUtils.isBlank(jsonpaths)) {
            jsonpaths = Default_ErrCode_Expr;
        }
        ParsedInfo<String> parsed = parseByPaths(obj, jsonpaths, String.class);
        return parsed == null || !parsed.isExpectValid() ? null : parsed.getResult();
    }

    public static Integer parseIntCode(Object obj, @Nullable String jsonpaths) {
        if (StringUtils.isBlank(jsonpaths)) {
            jsonpaths = Default_ErrCode_Expr;
        }
        ParsedInfo<Integer> parsed = parseByPaths(obj, jsonpaths, Integer.class);
        return parsed == null || !parsed.isExpectValid() ? null : parsed.getResult();
    }

    public static String parseErrMsg(Object obj, @Nullable String jsonpaths) {
        if (obj instanceof Throwable) {
            return ExceptionUtil.parseException((Throwable) obj).getErrorMsg();
        }
        if (StringUtils.isBlank(jsonpaths)) {
            jsonpaths = Default_ErrMsg_Expr;
        }
        ParsedInfo<String> parsed = parseByPaths(obj, jsonpaths, String.class);
        return parsed == null || !parsed.isExpectValid() ? null : parsed.getResult();
    }

    public static boolean parseBoolVal(Object obj, @Nullable String jsonpaths, boolean defaultVal) {
        Boolean b = parseBoolean(obj, jsonpaths);
        return b == null ? defaultVal : b;
    }

    public static Boolean parseBoolean(Object obj, @Nullable String jsonpaths) {
        if (obj instanceof Throwable) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (StringUtils.isBlank(jsonpaths)) {
            jsonpaths = Default_Bool_Expr;
        }
        ParsedInfo<Boolean> parsed = parseByPaths(obj, jsonpaths, Boolean.class);
        return parsed == null ? null : parsed.isExpectValid() ? parsed.getResult() : false;
    }

    public static <T> ParsedInfo<T> parseByPaths(Object obj, @NotNull String jsonpaths, @NotNull Class<T> resultCls) {
        return parseByPaths(obj, SplitterUtil.splitByComma(jsonpaths), resultCls);
    }

    /**
     * 从多个备选路径中依次解析结果，返回第一个找到的结果
     */
    private static <T> ParsedInfo<T> parseByPaths(Object obj, @NotNull List<String> jsonpaths, @NotNull Class<T> resultCls) {
        if (obj == null) {
            return null;
        }
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(jsonpaths), "解析路径不能为空");
        List<ParsedInfo<T>> parsedList = new ArrayList<>();
        for (String path : jsonpaths) {
            ParsedInfo<T> ret = parse(obj, path, resultCls);
            if (ret != null) {
                if (ret.isExpectValid()) {
                    return ret;
                } else {
                    parsedList.add(ret);
                }
            }
        }
        //如果均未找到符合期望的，但有字段被成功匹配，则返回匹配到的第一个
        return parsedList.isEmpty() ? null : parsedList.get(0);
    }

    /**
     * 从指定的路径中解析结果
     */
    private static <T> ParsedInfo<T> parse(Object obj, String jsonpath, Class<T> resultCls) {
        if (obj == null) {
            return null;
        }
        Preconditions.checkArgument(StringUtils.isNotBlank(jsonpath), "解析路径不能为空");
        Preconditions.checkNotNull(resultCls, "目标值类型不能为空");
        Preconditions.checkArgument(StringUtils.contains(jsonpath, "$."), "解析路径非法，需要以\"$.\"指定解析路径的根");
        try {
            String path = jsonpath;
            String expect = null;
            if (jsonpath.contains(EXPECT_SPLITTER1)) {
                String[] splits = jsonpath.split(EXPECT_SPLITTER1);
                path = splits[0].trim();
                expect = splits.length > 1 ? splits[1].trim() : null;
            } else if (jsonpath.contains(EXPECT_SPLITTER2)) {
                String[] splits = jsonpath.split(EXPECT_SPLITTER2);
                path = splits[0].trim();
                expect = splits.length > 1 ? splits[1].trim() : null;
            }
            Object val = parseObjVal(obj, path);
            if (val == null) {
                return null;
            }
            ParsedInfo<T> p = new ParsedInfo<>(resultCls);
            p.setValue(val);
            p.setExpect(expect);
            return p;
        } catch (Exception e) {
            log.warn(Constants.SYSTEM_ERROR_PREFIX +"resultParser parse error:{}", e.getMessage());
            return null;
        }
    }

    private static Object parseObjVal(Object obj, String path) {
        boolean isMethod = path.contains("(") && path.contains(")");
        if (!isMethod) {
            return evalVal(obj, path);
        }
        String methodName = StringUtils.remove(StringUtils.remove(StringUtils.remove(path, "$."), ")"), "(").trim();
        try {
            return ReflectUtil.invokeMethod(obj, methodName);
        } catch (Throwable e) {
            log.warn(Constants.SYSTEM_ERROR_PREFIX + "resultParser parseObjVal error:{}", e.getMessage());
            Throwable tx = ExceptionUtil.getRealException(e);
            if (tx instanceof NoSuchMethodException || (tx.getMessage() != null && tx.getMessage().contains("No such method"))) {
                return evalVal(obj, path);
            }
        }
        return null;
    }

    private static Object evalVal(Object root, String path) {
        try {
            return JSONPath.eval(root, path);
        } catch (Throwable e) {
            log.warn(Constants.SYSTEM_ERROR_PREFIX + "resultParser evalVal error:{}", e.getMessage());
        }
        return null;
    }
}
