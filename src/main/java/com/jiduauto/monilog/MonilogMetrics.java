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


    private static final CompositeMeterRegistry MONILOG_REGISTRY = getMetricsRegistry();;


    private static CompositeMeterRegistry getMetricsRegistry() {
        CompositeMeterRegistry registry = Metrics.globalRegistry;
        registry.config().onMeterAdded(METRICS_CONSUMER);
        return registry;
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
        private final AtomicInteger METERS_COUNTER = new AtomicInteger();

        private Map<Meter.Id, Meter> idMeterMap;

        public MoniLogMetricsConsumer(){
            CompositeMeterRegistry registry = Metrics.globalRegistry;
            idMeterMap = ReflectUtil.getPropValue(registry, "meterMap", true);
        }

        @Override
        public void accept(Meter meter) {
            if (!meter.getId().getName().startsWith(METRIC_PREFIX)) {
                return;
            }
            if (idMeterMap.get(meter.getId()) == null) {
                incrementCounter();
            }
        }



        @NotNull
        @Override
        public Consumer<Meter> andThen(@NotNull Consumer<? super Meter> after) {
            return Consumer.super.andThen(after);
        }

        public int getCurrentCounterValue() {
            return METERS_COUNTER.get();
        }

        public boolean isCounterExceededThreshold() {
            int threshold = 10000;
            return getCurrentCounterValue() > threshold;
        }

        private void incrementCounter() {
            METERS_COUNTER.incrementAndGet();
        }

    }

}
