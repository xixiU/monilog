package com.jiduauto.monilog;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;


class MybatisMoniLogInterceptor {
    @Intercepts({
            @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
            @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
            @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
    })
    @Slf4j
    static class MybatisInterceptor implements Interceptor {
        private static final Map<String, Class<?>> CACHED_CLASS = new HashMap<>();


        @SneakyThrows
        @Override
        public Object intercept(Invocation invocation) {
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
                logParams.setService(invocationInfo.serviceCls.getSimpleName());
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
                    MoniLogUtil.innerDebug( "mybatisInterceptor process error", e);
                    return obj;
                }
            } finally {
                logParams.setCost(costTime < 0 ? System.currentTimeMillis() - nowTime + 1 : costTime);
                MoniLogUtil.log(logParams);
            }
        }

        private static class MybatisInvocationInfo {
            Class<?> serviceCls;
            String methodName;

            String sql;
        }

        private static MybatisInvocationInfo parseMybatisExecuteInfo(Invocation invocation) {
            Class<?> serviceCls = invocation.getTarget().getClass();
            String methodName = invocation.getMethod().getName();
            String sql = "[parseSqlFailed]";
            try {
                Object expectedStatementHandler = getStatementHandlerObject(invocation);
                if (expectedStatementHandler != null) {
                    StatementHandler statementHandler = (StatementHandler) expectedStatementHandler;
                    MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
                    MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
                    String mapperId = mappedStatement.getId();
                    serviceCls = loadCls(mapperId.substring(0, mapperId.lastIndexOf('.')));
                    methodName = mapperId.substring(mapperId.lastIndexOf('.') + 1);
                    BoundSql boundSql = statementHandler.getBoundSql();
                    //去除sql中的注释、换行、多余空格等
                    sql = boundSql.getSql().replaceAll("--[^\n|\\\\n].+(\n|\\\\n)","").replaceAll("(\\\\n)+|\n+|\r+|\\s+"," ");
                }
            } catch (Throwable e) {
                MoniLogUtil.innerDebug( "parseMybatisExecuteInfo error", e);
            }
            MybatisInvocationInfo info = new MybatisInvocationInfo();
            info.serviceCls = serviceCls;
            info.methodName = methodName;
            info.sql = sql;
            return info;
        }

        private static Class<?> loadCls(String className){
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

        private static Object getStatementHandlerObject(Invocation invocation) {
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
            return expectedStatementHandler;
        }
    }
}
