package com.jiduauto.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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

    ResultParseStrategy resultParseStrategy() default ResultParseStrategy.IfSuccess;

    String boolExpr() default ResultParser.Default_Bool_Expr;

    String errorCodeExpr() default ResultParser.Default_ErrCode_Expr;

    String errorMsgExpr() default ResultParser.Default_ErrMsg_Expr;
}
