package com.jiduauto.monilog;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author yp
 * @date 2023/12/11
 */
@Slf4j
class MonilogMetrics {
    public static final String METRIC_PREFIX = "monilog_";

    private static final MoniLogMetricsConsumer METRICS_CONSUMER = new MoniLogMetricsConsumer();


    // 最大meters数目,系统所有的meter
    private static final CompositeMeterRegistry MONILOG_REGISTRY = getRegistry();


    private static CompositeMeterRegistry getRegistry() {
        CompositeMeterRegistry init = Metrics.globalRegistry;
        init.config().onMeterAdded(METRICS_CONSUMER);
        return init;
    }



    static void record(String metricName, String... tags) {
        try {
            if (METRICS_CONSUMER.isCounterExceededThreshold()) {
                log.error("too many metrics.current size:{}",METRICS_CONSUMER.getCurrentCounterValue());
                return;
            }
            MONILOG_REGISTRY.counter(metricName, tags).increment();
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }


    static Timer eventDuration(String metricName, String... tags) {
        try {
            return Timer.builder(metricName).tags(tags).publishPercentiles(new double[]{0.75, 0.95, 0.99, 1.0}).register(MONILOG_REGISTRY);
        } catch (Exception e) {
            log.warn(e.getMessage());
            return null;
        }
    }

    static class MoniLogMetricsConsumer implements Consumer<Meter> {
        private final AtomicInteger MAX_METERS_SIZE = new AtomicInteger();

        private Object meterMap = ReflectUtil.getPropValue(MONILOG_REGISTRY, "meterMap", true);

        @Override
        public void accept(Meter meter) {
            if (!meter.getId().getName().startsWith(METRIC_PREFIX)) {
                return;
            }
            if (meterMap == null) {
                // 此处存在循环依赖
                meterMap = ReflectUtil.getPropValue(MONILOG_REGISTRY, "meterMap", true);;
            }
            Map<Meter.Id, Meter> idMeterMap = (Map<Meter.Id, Meter>) meterMap;
            if (idMeterMap.get(meter.getId())==null) {
                incrementCounter();
            }
        }

        @NotNull
        @Override
        public Consumer<Meter> andThen(@NotNull Consumer<? super Meter> after) {
            return Consumer.super.andThen(after);
        }

        public int getCurrentCounterValue() {
            return MAX_METERS_SIZE.get();
        }

        public boolean isCounterExceededThreshold() {
            int threshold = 10000;
            return getCurrentCounterValue() > threshold;
        }

        private void incrementCounter() {
            MAX_METERS_SIZE.incrementAndGet();
        }

    }

}
