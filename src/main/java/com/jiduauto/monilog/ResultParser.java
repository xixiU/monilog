package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONPath;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

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
    public static final String Default_Bool_Expr = "$.success,$.succeeded,$.succeed,$.succ,$.code=SUCCESS,$.Code=SUCCESS,$.isOk(),$.isSuccess(),$.getSuccess(),$.isSucceed(),$.getSucceed(),$.isSucceeded(),$.getSucceeded(),$.isSucc(),$.getResult()";
    /**
     * 默认的错误码解析路径，注意对于基于方法的解析方式，仅支持无参方法
     */
    public static final String Default_ErrCode_Expr = "$.msgCode,$.resultCode,$.errorCode,$.responseCode,$.retCode,$.code,$.Code,$.status,$.getMsgCode(),$.getResultCode(),$.getErrorCode(),$.getResponseCode(),$.getRetCode(),$.getCode()";
    /**
     * 默认的错误原因解析路径，注意对于基于方法的解析方式，仅支持无参方法
     */
    public static final String Default_ErrMsg_Expr = "$.msgInfo,$.MsgInfo,$.message,$.Message,$.msg,$.Msg,$.resultMsg,$.errorMsg,$.errMsg,$.responseMsg,$.responseMessage,$.retMsg,$.subResultCode,$.errorDesc,$.error,$.getMsgInfo(),$.getMessage(),$.getMsg(),$.getResultMsg(),$.getErrorMsg(),$.getResponseMsg(),$.getRetMsg()";

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
        return parsed == null ? null : parsed.getCompatibleResult();
    }

    public static Integer parseIntCode(Object obj, @Nullable String jsonpaths) {
        if (StringUtils.isBlank(jsonpaths)) {
            jsonpaths = Default_ErrCode_Expr;
        }
        ParsedInfo<Integer> parsed = parseByPaths(obj, jsonpaths, Integer.class);
        return parsed == null ? null : parsed.getCompatibleResult();
    }

    public static String parseErrMsg(Object obj, @Nullable String jsonpaths) {
        if (obj instanceof Throwable) {
            return ExceptionUtil.parseException((Throwable) obj).getErrorMsg();
        }
        if (StringUtils.isBlank(jsonpaths)) {
            jsonpaths = Default_ErrMsg_Expr;
        }
        ParsedInfo<String> parsed = parseByPaths(obj, jsonpaths, String.class);
        return parsed == null ? null : parsed.getCompatibleResult();
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
        //与其它的判别逻辑不同的是，对于bool类型的结果， 如果实际结果与判别式不兼容，则返回false，而不是null。
        //因为这种类型字段会影响下游ResultParseStrategy的结果，而且即使与判别式不兼容也至少表明匹配到了路径中的同名字段，
        //例如: 判别式是：$.success=true，但响应结果中success=1，此时的判别结果应当是false，而不是null
        return parsed == null ? null : parsed.isExpectCompatible() && Boolean.TRUE.equals(parsed.getResult());
    }

    public static <T> ParsedInfo<T> parseByPaths(Object obj, @NotNull String jsonpaths, @NotNull Class<T> resultCls) {
        try{
            return parseByPaths(obj, SplitterUtil.splitByComma(jsonpaths), resultCls);
        }catch (Exception e){
            MoniLogUtil.innerDebug("parseByPaths error, obj:{}", e);
            return null;
        }
    }

    /**
     * 从多个备选路径中依次解析结果，返回找到的第一个最匹配的结果
     */
    private static <T> ParsedInfo<T> parseByPaths(Object obj, @NotNull List<String> jsonpaths, @NotNull Class<T> resultCls) {
        if (obj == null) {
            return null;
        }
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(jsonpaths), "解析路径不能为空");
        List<ParsedInfo<T>> parsedList = new ArrayList<>();
        List<ParsedInfo<T>> notCompatible = new ArrayList<>();
        for (String path : jsonpaths) {
            ParsedInfo<T> ret = parse(obj, path, resultCls);
            if (ret == null) {
                continue;
            }
            if (!ret.isExpectCompatible()) {//结果不兼容，则一定不是期望结果
                notCompatible.add(ret);
                continue;
            }
            T val = ret.getResult();
            if (ParsedInfo.isBool(resultCls) && val != null && (Boolean) val) {
                return ret;
            } else {//结果类型兼容，但值是空的，表示没取到。例如$.getMsg()，匹配到路径了，但结果中值是null
                parsedList.add(ret);
            }
        }
        //如果均未找到符合期望的，但有字段名被遍历到过，则返回最匹配到那个结果
        return parsedList.isEmpty() ? (notCompatible.isEmpty() ? null : notCompatible.get(0)) : parsedList.get(0);
    }

    /**
     * 从指定的路径中解析结果
     */
    private static <T> ParsedInfo<T> parse(Object obj, String jsonpath, Class<T> resultCls) {
        if (obj == null) {
            return null;
        }
        if (StringUtils.isBlank(jsonpath)) {
            MoniLogUtil.innerDebug("解析路径不能为空");
            return null;
        }
        if (resultCls == null) {
            MoniLogUtil.innerDebug("目标值类型不能为空");
            return null;
        }
        if (!StringUtils.contains(jsonpath,"$.")) {
            MoniLogUtil.innerDebug("解析路径非法:{}，需要以\"$.\"指定解析路径的根", jsonpath);
            return null;
        }
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
            TempResult tr = parseObjVal(obj, path);
            //这里解析到的结果有三种情况：1是没有找到对应的path，此时返回null，2是找到了path但未取到值，此时foundPath=true,value=null，3是找到了path且取到了值，此时value!=null&foundPath=true
            if (tr == null || !tr.foundPath) {
                return null;
            }
            ParsedInfo<T> p = new ParsedInfo<>(resultCls);
            p.setValue(tr.value);
            p.setExpect(expect);
            return p;
        } catch (Exception e) {
            MoniLogUtil.innerDebug("resultParser parse error, obj:{}, path:{}, resultCls:{}", JSON.toJSONString(obj), jsonpath, resultCls, e);
            return null;
        }
    }

    private static TempResult parseObjVal(Object obj, String path) {
        boolean isMethod = path.contains("(") && path.contains(")");
        if (!isMethod) {
            try {
                boolean foundPath = !(obj instanceof String && "null".equalsIgnoreCase((String) obj)) && !ObjectUtils.isEmpty(obj) && JSONPath.contains(obj, path);
                return new TempResult(foundPath ? JSONPath.eval(obj, path) : null, foundPath);
            } catch (NullPointerException e) {
                return new TempResult(null, false);
            } catch (Throwable e) {
                MoniLogUtil.innerDebug("resultParser evalVal error, obj:{}, path:{}", JSON.toJSONString(obj), path, e);
            }
            return null;
        }
        String methodName = StringUtils.remove(StringUtils.remove(StringUtils.remove(path, "$."), ")"), "(").trim();
        try {
            Method method = ReflectUtil.getMethodWithoutException(obj, methodName, new Object[]{});
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return new TempResult(method.invoke(obj));
        } catch (Throwable e) {
            MoniLogUtil.innerDebug("resultParser parseObjVal error, obj:{}, path:{}", JSON.toJSONString(obj), path, e);
            Throwable tx = ExceptionUtil.getRealException(e);
            if (tx instanceof NoSuchMethodException || (tx.getMessage() != null && tx.getMessage().contains("No such method"))) {
                return null;
            }
        }
        return null;
    }

    public static String mergeBoolExpr(String globalDefaultBoolExprs, String defaultBoolExprs) {
        if (StringUtils.isBlank(globalDefaultBoolExprs)) {
            return defaultBoolExprs;
        }
        if (StringUtils.isBlank(defaultBoolExprs)) {
            return globalDefaultBoolExprs;
        }
        String globalDefaultBoolExpr = StringUtils.stripStart(globalDefaultBoolExprs.replaceAll(EXPECT_SPLITTER1, EXPECT_SPLITTER2), "+");
        String defaultBoolExpr = StringUtils.stripStart(defaultBoolExprs.replaceAll(EXPECT_SPLITTER1, EXPECT_SPLITTER2), "+");
        List<String> globalExprList = SplitterUtil.splitByComma(globalDefaultBoolExpr);
        List<String> defaultExpr = SplitterUtil.splitByComma(defaultBoolExpr);
        String prefix = globalDefaultBoolExprs.startsWith("+") || defaultBoolExprs.startsWith("+") ? "+" : "";
        Set<String> set = new HashSet<>(globalExprList);
        set.addAll(defaultExpr);
        return prefix + String.join(",", set);
    }

    private static class TempResult {
        Object value;
        boolean foundPath;
        public TempResult(Object value) {
            this(value, true);
        }

        public TempResult(Object value, boolean foundPath) {
            this.value = value;
            this.foundPath = foundPath;
        }
    }
}
