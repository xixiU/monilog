package com.jiduauto.log.core;

import com.alibaba.fastjson.JSON;
import com.jiduauto.log.core.aop.MonitorLogAop;
import com.jiduauto.log.core.model.MonitorLogParams;
import com.jiduauto.log.core.model.MonitorLogProperties;
import com.jiduauto.log.core.util.MonitorLogUtil;
import com.jiduauto.log.core.util.MonitorSpringUtils;
import com.jiduauto.log.core.util.MonitorStringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;

/**
 * @author yp
 * @date 2023/07/12
 */
@Configuration
@EnableConfigurationProperties(MonitorLogProperties.class)
@ConditionalOnProperty(prefix = "monitor.log", name = "enable", matchIfMissing = true)
@Import({MonitorSpringUtils.class})
public class CoreMonitorLogConfiguration {
    @Resource
    private MonitorLogProperties monitorLogProperties;

    @Bean
    public MonitorLogAop aspectProcessor() {
        return new MonitorLogAop();
    }

    @ConditionalOnMissingBean(MonitorLogPrinter.class)
    @Bean
    public MonitorLogPrinter monitorLogPrinter() {
        return new DefaultMonitorLogPrinter();
    }

    /**
     * @author rongjie.yuan
     * @description: 默认日志打印方式
     * @date 2023/7/25 20:55
     */
    class DefaultMonitorLogPrinter implements MonitorLogPrinter {
        @Override
        public void log(MonitorLogParams logParams) {
            if (logParams == null) {
                return;
            }
            Class<?> serviceCls = logParams.getServiceCls();
            if (serviceCls == null) {
                serviceCls = MonitorLogUtil.class;
            }
            Logger logger = LoggerFactory.getLogger(serviceCls);
            String logPoint = logParams.getLogPoint().name();
            String service = logParams.getService();
            String action = logParams.getAction();
            String success = logParams.isSuccess() ? "Y" : "N";
            String code = logParams.getMsgCode();
            String msg = logParams.getMsgInfo();
            String rt = logParams.getCost() + "ms";
            String input = formatLongText(logParams.getInput());
            String output = formatLongText(logParams.getOutput());
            Throwable ex = logParams.getException();

            if (!logParams.isSuccess()) {
                logger.error("monitor log[{}]-{}.{} {}-{}-{} {} input:{}, output:{}", logPoint, service, action, success, code, msg, rt, input, output, ex);
                return;
            }
            boolean needLog = needLog(logPoint, service, action);
            if (!needLog) {
                return;
            }
            String tags = JSON.toJSONString(MonitorLogUtil.processTags(logParams));
            logger.info("monitor log[{}]-{}.{} {}-{}-{} {} input:{}, output:{}, tags:{}", logPoint, service, action, success, code, msg, rt, input, output, tags);
        }

        private boolean needLog(String logPoint, String service, String action) {
            MonitorLogProperties.PrinterLogProperties printer = monitorLogProperties.getPrinter();
            if (MonitorStringUtil.checkPathMatch(printer.getInfoExcludeLogPoint(), logPoint)) {
                return false;
            }
            if (MonitorStringUtil.checkPathMatch(printer.getInfoExcludeService(), service)) {
                return false;
            }
            if (MonitorStringUtil.checkPathMatch(printer.getInfoExcludeAction(), action)) {
                return false;
            }
            return true;
        }

        private String formatLongText(Object o) {
            int maxTextLen = monitorLogProperties.getPrinter().getMaxTextLen();
            if (o == null || o instanceof String) {
                return (String) o;
            }
            String str = JSON.toJSONString(o);
            if (str.length() > maxTextLen) {
                return str.substring(0, maxTextLen - 3) + "...";
            }
            return str;
        }
    }
}
