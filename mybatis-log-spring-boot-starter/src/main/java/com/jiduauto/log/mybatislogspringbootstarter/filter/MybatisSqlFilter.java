package com.jiduauto.log.mybatislogspringbootstarter.filter;

import com.jiduauto.log.constant.Constants;
import com.jiduauto.log.enums.LogPoint;
import com.jiduauto.log.model.MonitorLogParams;
import com.jiduauto.log.util.MonitorUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

/**
 * @author ：xiaoxu.bao
 * @date ：2022/11/14 21:39
 */
@Configuration
@ConditionalOnProperty(prefix = "monitor.log.mybatis", name = "enable", havingValue = "true", matchIfMissing = true)
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class})
})
@Slf4j
public class MybatisSqlFilter implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws InvocationTargetException, IllegalAccessException {
        long nowTime = System.currentTimeMillis();

        MonitorLogParams logParams = new MonitorLogParams();
        // 获取调用的目标对象
        Object target = invocation.getTarget();
        logParams.setLogPoint(LogPoint.DAL_CLIENT);
        logParams.setService(target.getClass().getSimpleName());
        logParams.setServiceCls(target.getClass());
        logParams.setAction(invocation.getMethod().getName());
        logParams.setInput(invocation.getArgs());
        try {
            Object obj = invocation.proceed();
            String sql = ((StatementHandler) target).getBoundSql().getSql();
            long costTime = System.currentTimeMillis() - nowTime;
            logParams.setCost(costTime);
            // 超过两秒的，打印错误日志
            if (costTime > Constants.SQL_TAKING_TOO_LONG) {
                log.error("sql cost time too long, sql{}, time:{}", sql, costTime);
            }
            logParams.setSuccess(true);

            return obj;
        } catch (Throwable e) {
            logParams.setCost(System.currentTimeMillis() - nowTime);
            logParams.setSuccess(false);
        }finally {
            MonitorUtil.log(logParams);
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

}
