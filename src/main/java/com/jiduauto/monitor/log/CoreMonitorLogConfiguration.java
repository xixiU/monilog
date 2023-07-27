package com.jiduauto.monitor.log;

import com.alibaba.fastjson.JSON;
import com.jiduauto.monitor.log.aop.MonitorLogAop;
import com.jiduauto.monitor.log.model.MonitorLogParams;
import com.jiduauto.monitor.log.model.MonitorLogProperties;
import com.jiduauto.monitor.log.util.MonitorLogUtil;
import com.jiduauto.monitor.log.util.MonitorSpringUtils;
import com.jiduauto.monitor.log.util.MonitorStringUtil;
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

    //@Bean
    public MonitorLogAop aspectProcessor() {
        return new MonitorLogAop();
    }

    @Bean
    @ConditionalOnMissingBean(MonitorLogPrinter.class)
    public MonitorLogPrinter monitorLogPrinter() {
        return new DefaultMonitorLogPrinter(monitorLogProperties.getPrinter());
    }

    /**
     * 默认日志打印方式
     */
    class DefaultMonitorLogPrinter implements MonitorLogPrinter {
        private MonitorLogProperties.PrinterProperties printerProperties;

        public DefaultMonitorLogPrinter(MonitorLogProperties.PrinterProperties printerProperties) {
            this.printerProperties = printerProperties;
        }

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
            if (MonitorStringUtil.checkPathMatch(printerProperties.getInfoExcludeComponents(), logPoint)) {
                return false;
            }
            if (MonitorStringUtil.checkPathMatch(printerProperties.getInfoExcludeServices(), service)) {
                return false;
            }
            if (MonitorStringUtil.checkPathMatch(printerProperties.getInfoExcludeActions(), action)) {
                return false;
            }
            return true;
        }

        private String formatLongText(Object o) {
            int maxTextLen = printerProperties.getMaxTextLen();
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
