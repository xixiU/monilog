package com.jiduauto.monilog;

import io.opentelemetry.api.trace.Span;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 监控测试报告
 *
 * @author yp
 * @date 2023/09/13
 */
public class TestReporter {
    private final List<LogItem> items;
    @Getter
    private final List<String> innerErrors;

    public TestReporter() {
        this.items = new ArrayList<>();
        this.innerErrors = new ArrayList<>();
    }

    public List<LogItem> getItems() {
        items.sort((o1, o2) -> (int) (o1.getTimestamp() - o2.getTimestamp()));
        return items;
    }

    synchronized TestReporter addLog(MoniLogParams item) {
        if (item == null) {
            return this;
        }
        this.items.add(new LogItem(item));
        return this;
    }

    synchronized TestReporter addInnerDebug(String innerError) {
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
