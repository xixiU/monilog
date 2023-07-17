package com.jiduauto.log.util;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * @author yepei
 */
public final class SplitterUtil {
    private static final String COMMA = ",";
    private static final String VERTICAL_LINE = "|";
    private static final String UNDER_LINE = "_";
    private static final String MIDDLE_LINE = "-";
    private static final Splitter COMMA_SPLITTER = Splitter.on(COMMA).omitEmptyStrings().trimResults();
    private static final Splitter COMMA_SPLITTER_WITH_EMPTY = Splitter.on(COMMA).trimResults();
    private static final Splitter VERTICAL_LINE_SPLITTER = Splitter.on(VERTICAL_LINE).omitEmptyStrings().trimResults();
    private static final Splitter UNDER_LINE_SPLITTER = Splitter.on(UNDER_LINE).omitEmptyStrings().trimResults();
    private static final Splitter MIDDLE_LINE_SPLITTER = Splitter.on(MIDDLE_LINE).omitEmptyStrings().trimResults();

    public enum Option {
        IGNORE_CONVERT_EXCEPTION,
        SKIP_NULL_VALUE,
        SKIP_REPEATED_VALUE;

        public static Option[] defaultOpt() {
            return new Option[]{IGNORE_CONVERT_EXCEPTION, SKIP_NULL_VALUE};
        }
    }

    public static <T> List<T> splitAndConvert(String val, String sep, Function<String, T> converter, Option... options) {
        List<String> list = split(val, sep);
        List<T> results = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return results;
        }
        Set<Option> optionSet = Sets.newHashSet(options == null ? Option.defaultOpt() : options);
        for (String s : list) {
            try {
                T t = converter.apply(s);
                if (optionSet.contains(Option.SKIP_NULL_VALUE)) {
                    if (t == null) {
                        continue;
                    }
                }
                if (optionSet.contains(Option.SKIP_REPEATED_VALUE)) {
                    if (t != null && results.contains(t)) {
                        continue;
                    }
                }
                results.add(t);
            } catch (Exception e) {
                if (!optionSet.contains(Option.IGNORE_CONVERT_EXCEPTION)) {
                    throw e;
                }
            }
        }
        return results;
    }

    public static List<String> splitByComma(String val) {
        return val == null ? new ArrayList<>() : toList(COMMA_SPLITTER.split(val));
    }

    public static List<String> splitByCommaWithEmpty(String val) {
        return val == null ? new ArrayList<>() : toList(COMMA_SPLITTER_WITH_EMPTY.split(val));
    }

    public static List<String> splitByVerticalLine(String val) {
        return val == null ? new ArrayList<>() : toList(VERTICAL_LINE_SPLITTER.split(val));
    }

    public static List<String> splitByUnderLine(String val) {
        return val == null ? new ArrayList<>() : toList(UNDER_LINE_SPLITTER.split(val));
    }

    public static List<String> splitByMiddleLine(String val) {
        return val == null ? new ArrayList<>() : toList(MIDDLE_LINE_SPLITTER.split(val));
    }

    public static List<String> split(String val, String seperator) {
        assert StringUtils.isNotBlank(seperator);
        if (COMMA.equals(seperator)) {
            return splitByComma(val);
        } else if (VERTICAL_LINE.equals(seperator)) {
            return splitByVerticalLine(val);
        } else if (UNDER_LINE.equals(seperator)) {
            return splitByUnderLine(val);
        } else if (MIDDLE_LINE.equals(seperator)) {
            return splitByMiddleLine(val);
        } else {
            Iterable<String> unmodifiedList = Splitter.on(seperator).trimResults().omitEmptyStrings().split(val);
            return toList(unmodifiedList);
        }
    }

    private static <T> List<T> toList(Iterable<T> it) {
        if (it == null) {
            return null;
        }
        return Lists.newArrayList(it);
    }
}
