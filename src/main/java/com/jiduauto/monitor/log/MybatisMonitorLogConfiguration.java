package com.jiduauto.monitor.log;

import com.jiduauto.monitor.log.constant.Constants;
import com.jiduauto.monitor.log.enums.ErrorEnum;
import com.jiduauto.monitor.log.enums.LogPoint;
import com.jiduauto.monitor.log.enums.MonitorType;
import com.jiduauto.monitor.log.model.ErrorInfo;
import com.jiduauto.monitor.log.model.MonitorLogParams;
import com.jiduauto.monitor.log.model.MonitorLogProperties;
import com.jiduauto.monitor.log.util.ExceptionUtil;
import com.jiduauto.monitor.log.util.MonitorLogUtil;
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
        private static final String SQL = "sql";
        private static final String SQL_COST_TOO_LONG = "sqlCostTooLang";

        public MybatisInterceptor(MonitorLogProperties.MybatisProperties mybatisProperties) {
            this.mybatisProperties = mybatisProperties;
        }

        @SneakyThrows
        @Override
        public Object intercept(Invocation invocation) {
            long nowTime = System.currentTimeMillis();

            MonitorLogParams logParams = new MonitorLogParams();
            long costTime = -1;
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

                logParams.setLogPoint(LogPoint.mybatis);
                logParams.setService(serviceCls.getSimpleName());
                logParams.setServiceCls(serviceCls);
                logParams.setAction(methodName);
                logParams.setSuccess(true);
                logParams.setMsgCode(ErrorEnum.SUCCESS.name());
                logParams.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                BoundSql boundSql = statementHandler.getBoundSql();
                String sql = boundSql.getSql().replace("\n+|\r+|\\s+", " ");
                logParams.setInput(new String[]{sql});
                //这句可能异常
                Object obj = invocation.proceed();
                logParams.setOutput(obj);
                costTime = System.currentTimeMillis() - nowTime + 1;
                logParams.setCost(costTime);
                // 超过两秒的，打印错误日志
                if (costTime > mybatisProperties.getLongQueryTime()) {
                    MetricMonitor.record(SQL_COST_TOO_LONG + MonitorType.RECORD.getMark());
                    log.error("sql_cost_time_too_long, sql{}, time:{}", sql, costTime);
                }
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
                logParams.setCost(costTime < 0 ? System.currentTimeMillis() - nowTime + 1 : costTime);
                MonitorLogUtil.log(logParams);
            }
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
