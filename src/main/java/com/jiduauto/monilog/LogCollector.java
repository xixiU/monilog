package com.jiduauto.monilog;

import io.opentelemetry.api.trace.Span;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 监控测试报告
 *
 * @author yp
 * @date 2023/09/13
 */
public class LogCollector {
    @Getter
    @Setter
    private volatile AtomicBoolean start = new AtomicBoolean(false);
    private final List<LogItem> items;
    @Getter
    private final List<String> innerErrors;

    public LogCollector() {
        this.items = new ArrayList<>();
        this.innerErrors = new ArrayList<>();
    }

    public void clear() {
        this.items.clear();
        this.innerErrors.clear();
    }

    public List<LogItem> getItems() {
        items.sort((o1, o2) -> (int) (o1.getTimestamp() - o2.getTimestamp()));
        return items;
    }

    synchronized LogCollector addLog(MoniLogParams item) {
        if (item == null) {
            return this;
        }
        this.items.add(new LogItem(item));
        return this;
    }

    synchronized LogCollector addInnerDebug(String innerError) {
        this.innerErrors.add(innerError);
        return this;
    }

    @Getter
    @Setter
    public static class LogItem {
        private long timestamp;
        private String traceId;
        private MoniLogParams params;

        LogItem(MoniLogParams item) {
            this.timestamp = System.currentTimeMillis();
            this.traceId = Span.current().getSpanContext().getTraceId();
            this.params = item;
        }
    }
}
