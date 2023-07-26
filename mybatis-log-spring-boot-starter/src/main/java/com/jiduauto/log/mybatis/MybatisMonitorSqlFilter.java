package com.jiduauto.log.mybatis;

import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.enums.MonitorType;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.metric.MetricMonitor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.util.*;

/**
 * @author ：xiaoxu.bao
 * @date ：2022/11/14 21:39
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),

})
@Slf4j
class MybatisMonitorSqlFilter implements Interceptor {

    /**
     * sql耗时过长
     */
    private static final String SQL = "sql";

    private static final String SQL_COST_TOO_LONG = "sqlCostTooLang";
    /**
     * 超时时间，单位毫秒，默认2000毫秒
     */
    @Value("${monitor.log.mybatis.long.query.time:2000}")
    private Long longQueryTime;

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
//        logParams.setInput(invocation.getArgs());
        List<String> tags  = new ArrayList<>();
        try {
            Object obj = invocation.proceed();
            BoundSql boundSql = statementHandler.getBoundSql();
            String sql = boundSql.getSql();
            long costTime = System.currentTimeMillis() - nowTime;
            logParams.setCost(costTime);
            tags.add(SQL);
            tags.add(sql);
            // 超过两秒的，打印错误日志
            if (costTime > longQueryTime) {
                MetricMonitor.record(SQL_COST_TOO_LONG +  MonitorType.RECORD.getMark(), tags.toArray(new String[0]));
                log.error("sql cost time too long, sql{}, time:{}", sql, costTime);
            }
            logParams.setSuccess(true);
            return obj;
        } catch (Throwable e) {
            log.error("intercept process error", e);
            logParams.setSuccess(false);
            logParams.setException(e);
        }finally {
            logParams.setTags(tags.toArray(new String[0]));
            logParams.setCost(System.currentTimeMillis() - nowTime);
            MonitorLogUtil.log(logParams);
        }
        return null;
    }

    private void getSQLParams(BoundSql boundSql){
        Object parameterObject = boundSql.getParameterObject();
        if (parameterObject instanceof MapperMethod.ParamMap) {
            MapperMethod.ParamMap paramMap = (MapperMethod.ParamMap) parameterObject;
            Collection values = paramMap.values();
            for (Object entry : paramMap.values()) {
                // TODO rongjie.yuan  2023/7/25 23:29
                // 测试发现这里要从LambdaQueryWrapper中取出对应参数，LambdaQueryWrapper是mybatis-plus中的,mybatis
//                if (entry instanceof LambdaQueryWrapper) {
//
//                }
            }
        }
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
