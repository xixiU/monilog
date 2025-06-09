package com.example.monilog;

/**
 * @author yepei
 */
public enum ResultParseStrategy {
    /**
     * 基于结果对象中的布尔字段判定结果是否成功，该策略是默认策略，如果接口方法返回值中没有指定用于判断success的信息(如响应中没有success相关字段)，则会使用IfNotException策略来判定结果是否成功
     */
    IfSuccess,
    /**
     * 基于结果是否为null判定结果是否成功
     */
    IfNotNull,
    /**
     * 基于结果是否为空或null判定结果是否成功，一般用于字符串或集合类型的结果
     */
    IfNotEmpty,
    /**
     * 基于执行时是否发生异常来判定结果是否成功
     */
    IfNotException
}
