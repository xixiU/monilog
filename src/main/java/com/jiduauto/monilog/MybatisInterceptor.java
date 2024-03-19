package com.jiduauto.monilog;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "queryCursor", args = {MappedStatement.class, Object.class, RowBounds.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
@Slf4j
public final class MybatisInterceptor implements Interceptor {
    private static final Map<String, Class<?>> CACHED_CLASS = new HashMap<>();
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT_THREAD_LOCAL = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));

    public static Interceptor getInstance() {
        return new MybatisInterceptor();
    }

    @SneakyThrows
    @Override
    public Object intercept(Invocation invocation) {
        if (!ComponentEnum.mybatis.isEnable()) {
            return invocation.proceed();
        }
        long nowTime = System.currentTimeMillis();
        MoniLogParams logParams = new MoniLogParams();
        logParams.setLogPoint(LogPoint.mybatis);
        logParams.setSuccess(true);
        logParams.setMsgCode(ErrorEnum.SUCCESS.name());
        logParams.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
        long costTime = -1;
        Throwable bizException = null;
        Object obj = null;
        try {
            // 获取调用的目标对象
            MybatisInvocationInfo invocationInfo = parseMybatisExecuteInfo(invocation);
            logParams.setServiceCls(invocationInfo.serviceCls);
            logParams.setService(ReflectUtil.getSimpleClassName(invocationInfo.serviceCls));
            logParams.setAction(invocationInfo.methodName);
            logParams.setInput(new String[]{invocationInfo.sql});
            try {
                obj = invocation.proceed();
            } catch (Throwable t) {
                bizException = t;
            }
            logParams.setOutput(obj);
            costTime = System.currentTimeMillis() - nowTime + 1;
            logParams.setCost(costTime);
            if (bizException == null) {
                return obj;
            } else {
                throw bizException;
            }
        } catch (Throwable e) {
            if (e == bizException) {//说明e是业务异常
                logParams.setSuccess(false);
                Throwable realException = ExceptionUtil.getRealException(e);
                logParams.setException(realException);
                ErrorInfo errorInfo = ExceptionUtil.parseException(e);
                if (errorInfo != null) {
                    logParams.setMsgCode(errorInfo.getErrorCode());
                    logParams.setMsgInfo(errorInfo.getErrorMsg());
                }
                throw e;
            } else {//组件异常
                MoniLogUtil.innerDebug("mybatisInterceptor process error", e);
                return obj;
            }
        } finally {
            logParams.setCost(costTime < 0 ? System.currentTimeMillis() - nowTime + 1 : costTime);
            MoniLogUtil.log(logParams);
        }
    }

    // 使用 Plugin 包装拦截器，不然如果业务系统也存在拦截器，会导致业务系统拦截器结构发生变化，变成代理类。
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    private static class MybatisInvocationInfo {
        Class<?> serviceCls;
        String methodName;

        String sql;

        MybatisInvocationInfo(Class<?> serviceCls, String methodName) {
            this.serviceCls = serviceCls;
            this.methodName = methodName;
        }
    }

    /**
     * 此方法可以拦截StatementHandler与Executor接口；
     * 在Executor接口中，当前拦截的方法第一个参数均是MappedStatement.class类型的对象,第二个参数均是Object.class类型的parameter参数
     *
     * @param invocation
     * @return
     */
    private static MybatisInvocationInfo parseMybatisExecuteInfo(Invocation invocation) {
        Class<?> serviceCls = invocation.getTarget().getClass();
        String methodName = invocation.getMethod().getName();
        MybatisInvocationInfo info = new MybatisInvocationInfo(serviceCls, methodName);
        String initSql = "[parseSqlFailed]";
        String sql = initSql;
        try {
            if (Executor.class.isAssignableFrom(serviceCls)) {
                Object[] args = invocation.getArgs();
                if (args != null && args.length >= 2 && MappedStatement.class.isAssignableFrom(args[0].getClass())) {
                    MappedStatement mappedStatement = (MappedStatement) args[0];
                    setServiceClsAndMethodName(mappedStatement, info);
                    try {
                        // 获取sql
                        BoundSql boundSql = mappedStatement.getBoundSql(args[1]);
                        sql = getSqlAndSetParams(boundSql, mappedStatement);
                    } catch (Throwable e) {
                        MoniLogUtil.innerDebug("parseMybatisExecuteInfo error#1", e);
                    }
                }
            }
            if (StringUtils.isNotBlank(sql) && !StringUtils.equals(sql, initSql)) {
                info.sql = sql;
                return info;
            }
            //这段代码走不到了
            if (StatementHandler.class.isAssignableFrom(serviceCls)) {
                StatementHandler statementHandler = getStatementHandlerObject(invocation);
                if (statementHandler != null) {
                    MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
                    MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
                    setServiceClsAndMethodName(mappedStatement, info);
                    BoundSql boundSql = statementHandler.getBoundSql();
                    //去除sql中的注释、换行、多余空格等
                    sql = getSqlAndSetParams(boundSql, mappedStatement);
                }
            }
        } catch (Throwable e) {
            MoniLogUtil.innerDebug("parseMybatisExecuteInfo error#2", e);
        }
        info.sql = sql;
        return info;
    }

    private static void setServiceClsAndMethodName(MappedStatement mappedStatement, MybatisInvocationInfo info) {
        String mapperId = mappedStatement.getId();
        info.serviceCls = loadCls(mapperId.substring(0, mapperId.lastIndexOf('.')));
        info.methodName = mapperId.substring(mapperId.lastIndexOf('.') + 1);
    }

    private static Class<?> loadCls(String className) {
        Class<?> aClass = CACHED_CLASS.get(className);
        if (aClass != null) {
            return aClass;
        }
        try {
            aClass = Class.forName(className);
            CACHED_CLASS.put(className, aClass);
        } catch (ClassNotFoundException ignored) {

        }
        return aClass;
    }

    private static StatementHandler getStatementHandlerObject(Invocation invocation) {
        Object expectedStatementHandler = invocation.getTarget();
        while (Proxy.isProxyClass(expectedStatementHandler.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(expectedStatementHandler);
            //fastReturn
            if (BooleanUtils.isNotTrue(metaObject.hasGetter("h.target"))) {
                break;
            }
            expectedStatementHandler = metaObject.getValue("h.target");
        }
        //failFast
        if (!(expectedStatementHandler instanceof StatementHandler)) {
            return null;
        }
        return (StatementHandler) expectedStatementHandler;
    }
    /**
     * 获取完整的sql实体的信息
     */
    private static String getSqlAndSetParams(BoundSql boundSql, MappedStatement ms) {
        String sql = formatSql(boundSql.getSql());
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Configuration configuration = ms.getConfiguration();
        if (configuration == null || StringUtils.isBlank(sql) || parameterMappings == null) {
            return sql;
        }
        //参考mybatis 源码 DefaultParameterHandler
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        Object param = boundSql.getParameterObject();
        for (ParameterMapping pm : parameterMappings) {
            if (pm.getMode() == ParameterMode.OUT) {
                continue;
            }
            Object value;
            String propertyName = pm.getProperty();
            if (boundSql.hasAdditionalParameter(propertyName)) {
                value = boundSql.getAdditionalParameter(propertyName);
            } else if (param == null) {
                value = null;
            } else if (typeHandlerRegistry.hasTypeHandler(param.getClass())) {
                value = param;
            } else {
                MetaObject metaObject = configuration.newMetaObject(param);
                value = metaObject.getValue(propertyName);
            }

            String paramValueStr;
            if (value instanceof String) {
                paramValueStr = "'" + value + "'";
            } else if (value instanceof Date) {
                paramValueStr = "'" + DATE_FORMAT_THREAD_LOCAL.get().format(value) + "'";
            } else {
                paramValueStr = value + "";
            }
            sql = sql.replaceFirst("\\?", paramValueStr);
        }
        return sql;
    }


    /**
     * 去除sql中的注释、换行、多余空格等
     */
    private static String formatSql(String sql) {
        if (StringUtils.isBlank(sql)) {
            return sql;
        }
        sql = sql.trim()
                //去掉注释
                .replaceAll("--[^\n|\\\\n].+(\n|\\\\n)", "")
                //去掉多余的换行或空格
                .replaceAll("(\\\\n)+|\n+|\r+|\\s+", " ")
                //去掉','左右的空格
                .replaceAll("\\s*,\\s*", ",");
        if (!sql.endsWith(";")) {
            sql += ";";
        }
        return sql;
    }
}