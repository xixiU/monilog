package com.jiduauto.monitor.log;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yepei
 */
final class SplitterUtil {
    private static final String COMMA = ",";
    private static final Splitter COMMA_SPLITTER = Splitter.on(COMMA).omitEmptyStrings().trimResults();

    public static List<String> splitByComma(String val) {
        return val == null ? new ArrayList<>() : toList(COMMA_SPLITTER.split(val));
    }

    private static <T> List<T> toList(Iterable<T> it) {
        if (it == null) {
            return null;
        }
        return Lists.newArrayList(it);
    }
}
