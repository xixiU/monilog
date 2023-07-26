package com.jiduauto.log.mybatis;

import com.jiduauto.log.core.ErrorInfo;
import com.jiduauto.log.core.enums.ErrorEnum;
import com.jiduauto.log.core.enums.LogPoint;
import com.jiduauto.log.core.enums.MonitorType;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.util.ExceptionUtil;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.metric.MetricMonitor;
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
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ：xiaoxu.bao
 * @date ：2022/11/14 21:39
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
})
@Slf4j
class MybatisInterceptor implements Interceptor {

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
    public Object intercept(Invocation invocation) {
        long nowTime = System.currentTimeMillis();

        MonitorLogParams logParams = new MonitorLogParams();
        List<String> tags = new ArrayList<>();
        long costTime = 0;
        try {
            // 获取调用的目标对象
            Object expectedStatementHandler = getStatementHandlerObject(invocation);
            if (expectedStatementHandler == null) {
                invocation.proceed();
                return null;
            }
            StatementHandler statementHandler = (StatementHandler) expectedStatementHandler;
            MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
            MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
            String mapperId = mappedStatement.getId();
            Class<?> serviceCls = Class.forName(mapperId.substring(0, mapperId.lastIndexOf('.')));
            String methodName = mapperId.substring(mapperId.lastIndexOf('.') + 1);

            logParams.setLogPoint(LogPoint.DAL_CLIENT);
            logParams.setService(serviceCls.getSimpleName());
            logParams.setServiceCls(serviceCls);
            logParams.setAction(methodName);
            logParams.setMsgCode(ErrorEnum.SUCCESS.name());
            logParams.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
            Object obj = invocation.proceed();
            BoundSql boundSql = statementHandler.getBoundSql();
            String sql = boundSql.getSql().replace("\n|\r|\\s+", " ");
            logParams.setInput(new String[]{sql});
            logParams.setOutput(obj);
            costTime = System.currentTimeMillis() - nowTime;
            logParams.setCost(costTime);
            tags.add(SQL);
            tags.add(sql);
            // 超过两秒的，打印错误日志
            if (costTime > longQueryTime) {
                MetricMonitor.record(SQL_COST_TOO_LONG + MonitorType.RECORD.getMark(), tags.toArray(new String[0]));
                log.error("sql cost time too long, sql{}, time:{}", sql, costTime);
            }
            logParams.setSuccess(true);
            return obj;
        } catch (Throwable e) {
            log.error("mysqlInterceptor process error", e);
            logParams.setSuccess(false);
            logParams.setException(e);
            ErrorInfo errorInfo = ExceptionUtil.parseException(e);
            if (errorInfo != null) {
                logParams.setMsgCode(errorInfo.getErrorCode());
                logParams.setMsgInfo(errorInfo.getErrorMsg());
            }
            throw e;
        } finally {
            logParams.setTags(tags.toArray(new String[0]));
            logParams.setCost(costTime);
            MonitorLogUtil.log(logParams);
        }
    }
    private Object getStatementHandlerObject(Invocation invocation) {
        Object expectedStatementHandler = invocation.getTarget();
        while (Proxy.isProxyClass(expectedStatementHandler.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(expectedStatementHandler);
            //fastReturn
            if (BooleanUtils.isNotTrue(metaObject.hasGetter("h.target"))) {
                log.error("can't find mappedStatement h.get method");
                break;
            }
            expectedStatementHandler = metaObject.getValue("h.target");
        }
        //failFast
        if (!(expectedStatementHandler instanceof StatementHandler)) {
            log.error("sorry, expectedStatementHandler not instanceof StatementHandler!");
            return null;
        }
        return expectedStatementHandler;
    }

}
