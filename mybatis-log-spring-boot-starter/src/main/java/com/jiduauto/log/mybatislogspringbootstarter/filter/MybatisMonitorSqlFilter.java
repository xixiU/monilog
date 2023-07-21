package com.jiduauto.log.mybatislogspringbootstarter.filter;

import com.jiduauto.log.enums.LogPoint;
import com.jiduauto.log.model.MonitorLogParams;
import com.jiduauto.log.mybatislogspringbootstarter.constant.MybatisLogConstant;
import com.jiduauto.log.util.MonitorLogUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author ：xiaoxu.bao
 * @date ：2022/11/14 21:39
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {MappedStatement.class, Object.class}),

})
@Slf4j
public class MybatisMonitorSqlFilter implements Interceptor {

    @SneakyThrows
    @Override
    public Object intercept(Invocation invocation) throws InvocationTargetException, IllegalAccessException {
        long nowTime = System.currentTimeMillis();

        MonitorLogParams logParams = new MonitorLogParams();
        // 获取调用的目标对象
        Object expectedStatementHandler = getStatementHandlerObject(invocation);
        if (expectedStatementHandler == null) {
            invocation.proceed();
            return null;
        }
        StatementHandler statementHandler = (StatementHandler) expectedStatementHandler;
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");

        logParams.setLogPoint(LogPoint.DAL_CLIENT);
        logParams.setService(mappedStatement.getId());
        logParams.setServiceCls(statementHandler.getClass());
        logParams.setAction(invocation.getMethod().getName());
        logParams.setInput(invocation.getArgs());
        List<String> tags  = new ArrayList<>();
        try {
            Object obj = invocation.proceed();
            String sql = statementHandler.getBoundSql().getSql();
            long costTime = System.currentTimeMillis() - nowTime;
            logParams.setCost(costTime);
            tags.add(MybatisLogConstant.SQL);
            tags.add(sql);
            logParams.setTags(tags.toArray(new String[0]));
            // 超过两秒的，打印错误日志
            if (costTime > MybatisLogConstant.SQL_TAKING_TOO_LONG) {
                log.error("sql cost time too long, sql{}, time:{}", sql, costTime);
            }
            logParams.setSuccess(true);
            return obj;
        } catch (Throwable e) {
            logParams.setCost(System.currentTimeMillis() - nowTime);
            logParams.setSuccess(false);
        }finally {
            MonitorLogUtil.log(logParams);
        }
        return null;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private Object getStatementHandlerObject(Invocation invocation){
        Object expectedStatementHandler = invocation.getTarget();
        while (Proxy.isProxyClass(expectedStatementHandler.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(expectedStatementHandler);
            //fastReturn
            if (BooleanUtils.isNotTrue(metaObject.hasGetter("h.target"))) {
                log.error("cant find mappedStatement h.get method");
                break;
            }
            expectedStatementHandler = metaObject.getValue("h.target");
        }
        //failFast
        if (!(expectedStatementHandler instanceof StatementHandler)) {
            log.error("sorry,expectedStatementHandler not instanceof StatementHandler!");
            return null;
        }
        return expectedStatementHandler;
    }

}
