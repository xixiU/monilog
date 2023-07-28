package com.jiduauto.monitor.log;

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
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.lang.reflect.Proxy;
import java.sql.Statement;

@EnableConfigurationProperties
@ConditionalOnProperty(prefix = "monitor.log.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("('${monitor.log.component.includes:*}'.equals('*') or '${monitor.log.component.includes}'.contains('mybatis')) and !('${monitor.log.component.excludes:}'.equals('*') or '${monitor.log.component.excludes:}'.contains('mybatis'))")
@ConditionalOnClass(CoreMonitorLogConfiguration.class)
@AutoConfigureAfter(CoreMonitorLogConfiguration.class)
@Configuration
class MybatisMonitorLogConfiguration {
    @Resource
    private MonitorLogProperties monitorLogProperties;

    @Bean
    public MybatisInterceptor mybatisMonitorSqlFilter() {
        return new MybatisInterceptor(monitorLogProperties.getMybatis());
    }

    /**
     * @author ：xiaoxu.bao
     * @date ：2022/11/14 21:39
     */
    @Intercepts({
            @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
            @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
    })
    @Slf4j
    static class MybatisInterceptor implements Interceptor {
        private final MonitorLogProperties.MybatisProperties mybatisProperties;
        private static final String SQL_COST_TOO_LONG = "sqlCostTooLang";

        public MybatisInterceptor(MonitorLogProperties.MybatisProperties mybatisProperties) {
            this.mybatisProperties = mybatisProperties;
        }

        @SneakyThrows
        @Override
        public Object intercept(Invocation invocation) {
            long nowTime = System.currentTimeMillis();
            MonitorLogParams logParams = new MonitorLogParams();
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
                // 超过时间阀值的，打印错误日志
                if (mybatisProperties != null && mybatisProperties.getLongQueryTime() > 0 && costTime > mybatisProperties.getLongQueryTime()) {
                    MetricMonitor.record(SQL_COST_TOO_LONG + MonitorType.RECORD.getMark());
                    log.error("sql_cost_time_too_long, sql{}, time:{}", invocationInfo.sql, costTime);
                }
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
                    log.warn(Constants.SYSTEM_ERROR_PREFIX + "mybatisInterceptor process error:{}", e.getMessage());
                    return obj;
                }
            } finally {
                logParams.setCost(costTime < 0 ? System.currentTimeMillis() - nowTime + 1 : costTime);
                MonitorLogUtil.log(logParams);
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
                    serviceCls = Class.forName(mapperId.substring(0, mapperId.lastIndexOf('.')));
                    methodName = mapperId.substring(mapperId.lastIndexOf('.') + 1);
                    BoundSql boundSql = statementHandler.getBoundSql();
                    sql = boundSql.getSql().replace("\n+|\r+|\\s+", " ");
                }
            } catch (Throwable e) {
                log.warn(Constants.SYSTEM_ERROR_PREFIX + "parseMybatisExecuteInfo error:{}", e.getMessage());
            }
            MybatisInvocationInfo info = new MybatisInvocationInfo();
            info.serviceCls = serviceCls;
            info.methodName = methodName;
            info.sql = sql;
            return info;
        }

        private static Object getStatementHandlerObject(Invocation invocation) {
            Object expectedStatementHandler = invocation.getTarget();
            while (Proxy.isProxyClass(expectedStatementHandler.getClass())) {
                MetaObject metaObject = SystemMetaObject.forObject(expectedStatementHandler);
                //fastReturn
                if (BooleanUtils.isNotTrue(metaObject.hasGetter("h.target"))) {
                    log.warn(Constants.SYSTEM_ERROR_PREFIX + "can't find mappedStatement h.get method");
                    break;
                }
                expectedStatementHandler = metaObject.getValue("h.target");
            }
            //failFast
            if (!(expectedStatementHandler instanceof StatementHandler)) {
                log.warn(Constants.SYSTEM_ERROR_PREFIX + "sorry, expectedStatementHandler not instanceof StatementHandler!");
                return null;
            }
            return expectedStatementHandler;
        }
    }
}
