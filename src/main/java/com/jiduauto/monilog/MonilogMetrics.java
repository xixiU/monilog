package com.jiduauto.monilog;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yp
 * @date 2023/12/11
 */
@Slf4j
class MonilogMetrics {
    static void record(String metricName, String... tags) {
        try {
            Metrics.globalRegistry.counter(metricName, tags).increment();
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    static void cumulation(String metricName, double cumulate, String... tags) {
        try {
            Metrics.globalRegistry.counter(metricName, tags).increment(cumulate);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    static DistributionSummary eventRange(String metricName, Double expextMin, Double expextMax, String... tags) {
        try {
            DistributionSummary summary = DistributionSummary.builder(metricName).tags(tags).maximumExpectedValue(expextMax).minimumExpectedValue(expextMin).publishPercentiles(new double[]{0.75, 0.95, 0.99, 1.0}).register(Metrics.globalRegistry);
            return summary;
        } catch (Exception e) {
            log.warn(e.getMessage());
            return null;
        }
    }

    static Timer eventDruation(String metricName, String... tags) {
        try {
            return Timer.builder(metricName).tags(tags).publishPercentiles(new double[]{0.75, 0.95, 0.99, 1.0}).register(Metrics.globalRegistry);
        } catch (Exception e) {
            log.warn(e.getMessage());
            return null;
        }
    }
}
